package com.cwnupaa.backend.config;

import com.cwnupaa.backend.vo.UserProfileVO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

/**
 * 应用层兜底限流。外层 Nginx 负责按 IP 和全站削峰，本过滤器再按登录用户限制，
 * 并保护会明显占用 CPU/内存的上传与 PDF 生成接口。
 *
 * 单机部署使用本地令牌桶即可；如果后续横向扩容，应把计数迁移到 Redis/网关层。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TrafficProtectionFilter extends OncePerRequestFilter {
    private static final Pattern SAFE_IP = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");
    private static final int MAX_TRACKED_CLIENTS = 50_000;

    private final Cache<String, TokenBucket> buckets = Caffeine.newBuilder()
            .maximumSize(MAX_TRACKED_CLIENTS)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();
    // 覆盖 200 人同时在线时的页面初始化/API 突发，网关仍会拦截异常洪水。
    private final TokenBucket globalBucket = new TokenBucket(800, 400, Duration.ofSeconds(1));
    // 普通文件上传和 multipart 导入共享同一组槽位，全站最多同时处理 30 路。
    private final Semaphore uploadSlots = new Semaphore(30, true);
    private final Semaphore pdfSlots = new Semaphore(200, true);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || "/error".equals(request.getRequestURI())
                // 鉴权状态同步不能和普通页面数据争抢令牌，否则权限变更通知后可能
                // 因 /user/me 被限流而继续使用旧权限快照。
                || "/user/me".equals(request.getRequestURI())
                || "/notification/stream".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!globalBucket.tryConsume()) {
            reject(request, response, HttpStatus.TOO_MANY_REQUESTS.value(), 1, "系统繁忙，请稍后重试");
            return;
        }

        // 服务器渲染（Next.js 容器）会直接访问后端，例如公告详情的 generateMetadata。
        // 这些请求没有经过网关、也就不带 X-Real-IP，若按 remoteAddr 计数，全站所有
        // 用户的 SSR 数据请求会共用同一个令牌桶并被误判为同一个客户端而触发 429。
        String bucketScope = isInternalCaller(request) ? null : clientIdentity(request);
        if (bucketScope != null) {
            LimitRule rule = selectRule(request);
            String key = rule.name() + ':' + bucketScope;
            TokenBucket bucket = buckets.get(key,
                    ignored -> new TokenBucket(rule.capacity(), rule.refillTokens(), rule.refillPeriod()));
            if (!bucket.tryConsume()) {
                reject(request, response, HttpStatus.TOO_MANY_REQUESTS.value(), rule.retryAfterSeconds(),
                        "访问过于频繁，请稍后重试");
                return;
            }
        }

        Semaphore workSlots = expensiveWorkSlots(request);
        if (workSlots != null && !workSlots.tryAcquire()) {
            reject(request, response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, 3,
                    "服务器正在处理其他耗时任务，请稍后重试");
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (workSlots != null) workSlots.release();
        }
    }

    private LimitRule selectRule(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod().toUpperCase(Locale.ROOT);

        if (isLogin(path)) return new LimitRule("login", 200, 60, Duration.ofMinutes(1));
        if (isVerificationCode(path)) return new LimitRule("verification-code", 200, 60, Duration.ofMinutes(1));
        if (isUpload(request)) return new LimitRule("upload", 60, 60, Duration.ofMinutes(1));
        if (isPdfWork(path)) return new LimitRule("pdf", 60, 60, Duration.ofMinutes(1));
        if (path.endsWith("/search")) return new LimitRule("search", 120, 120, Duration.ofMinutes(1));
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            return new LimitRule("write", 120, 120, Duration.ofMinutes(1));
        }
        // React 页面刷新会并行加载多个独立数据块。突发容量与网关保持一致，
        // 平均速率仍由网关和全局桶约束，避免一次正常刷新误伤为 429。
        return new LimitRule("read", 300, 100, Duration.ofSeconds(1));
    }

    private Semaphore expensiveWorkSlots(HttpServletRequest request) {
        if (isUpload(request)) return uploadSlots;
        if (isPdfWork(request.getRequestURI())) return pdfSlots;
        return null;
    }

    private boolean isLogin(String path) {
        return "/user/login".equals(path)
                || "/user/login-by-code".equals(path)
                || "/user/external/login".equals(path);
    }

    private boolean isVerificationCode(String path) {
        return "/user/send-code".equals(path) || "/user/external/send-code".equals(path);
    }

    private boolean isUpload(HttpServletRequest request) {
        String contentType = request.getContentType();
        return "/file/upload".equals(request.getRequestURI())
                || (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/"));
    }

    private boolean isPdfWork(String path) {
        return path.equals("/certificate/grant/distribute")
                || path.equals("/certificate/grant/template/preview")
                || path.startsWith("/certificate/grant/download")
                || path.startsWith("/certificate/grant/preview")
                || path.startsWith("/certificate/grant/guest/download")
                || path.startsWith("/certificate/grant/guest/preview")
                || path.equals("/event/admission-ticket");
    }

    /**
     * 判断是否为容器内部的服务器间调用。网关始终会写入 X-Real-IP，因此缺少该头
     * 且来源地址属于回环/私有网段时，只可能是同网络中的前端容器。后端端口未对
     * 公网发布，此类调用不会绕过网关到达，可以不做按客户端计数。
     */
    private boolean isInternalCaller(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return false;
        return isPrivateAddress(request.getRemoteAddr());
    }

    private boolean isPrivateAddress(String address) {
        if (address == null || address.isBlank()) return true;
        if ("127.0.0.1".equals(address) || "::1".equals(address) || "localhost".equalsIgnoreCase(address)) return true;
        if (address.startsWith("10.") || address.startsWith("192.168.") || address.startsWith("169.254.")) return true;
        if (address.startsWith("172.")) {
            String[] parts = address.split("\\.");
            if (parts.length > 1) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        if (address.startsWith("fc") || address.startsWith("fd") || address.startsWith("fe80")) return true;
        return false;
    }

    private String clientIdentity(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object sessionUser = session == null ? null : session.getAttribute("user");
        if (sessionUser instanceof UserProfileVO user && user.getUserId() != null) {
            return "user:" + user.getUserId();
        }
        return "ip:" + clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && SAFE_IP.matcher(realIp.trim()).matches()) return realIp.trim();
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response,
                        int status, long retryAfterSeconds, String message)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Retry-After", Long.toString(Math.max(1, retryAfterSeconds)));
        response.setHeader("Cache-Control", "no-store");
        if (expectsHtmlDocument(request)) {
            response.setContentType(MediaType.TEXT_HTML_VALUE);
            String title = status == HttpServletResponse.SC_SERVICE_UNAVAILABLE
                    ? "服务正在缓冲" : "当前访问人数较多";
            response.getWriter().write("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                    + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>" + title
                    + "</title></head><body><main><h1>" + title + "</h1><p>系统将在 <strong id=\"seconds\">10</strong> 秒后自动重试。</p>"
                    + "</main><script>(function(){var n=10,e=document.getElementById('seconds'),t=setInterval(function(){n--;e.textContent=n;if(n<=0){clearInterval(t);location.reload();}},1000);})();</script></body></html>");
            return;
        }
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":" + status + ",\"msg\":\"" + message + "\",\"data\":null}");
    }

    private boolean expectsHtmlDocument(HttpServletRequest request) {
        String fetchDestination = request.getHeader("Sec-Fetch-Dest");
        if ("document".equalsIgnoreCase(fetchDestination)) return true;
        String accept = request.getHeader("Accept");
        return accept != null && accept.toLowerCase(Locale.ROOT).contains(MediaType.TEXT_HTML_VALUE);
    }

    private record LimitRule(String name, int capacity, int refillTokens, Duration refillPeriod) {
        long retryAfterSeconds() {
            return Math.max(1, (long) Math.ceil((double) refillPeriod.toSeconds() / refillTokens));
        }
    }

    static final class TokenBucket {
        private final int capacity;
        private final double tokensPerNanosecond;
        private double tokens;
        private long lastRefillNanos;

        TokenBucket(int capacity, int refillTokens, Duration refillPeriod) {
            if (capacity <= 0 || refillTokens <= 0 || refillPeriod.isZero() || refillPeriod.isNegative()) {
                throw new IllegalArgumentException("令牌桶参数必须为正数");
            }
            this.capacity = capacity;
            this.tokens = capacity;
            this.tokensPerNanosecond = (double) refillTokens / refillPeriod.toNanos();
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryConsume() {
            long now = System.nanoTime();
            long elapsed = Math.max(0, now - lastRefillNanos);
            tokens = Math.min(capacity, tokens + elapsed * tokensPerNanosecond);
            lastRefillNanos = now;
            if (tokens < 1.0d) return false;
            tokens -= 1.0d;
            return true;
        }
    }
}
