package com.cwnupaa.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TrafficProtectionFilterTests {

    @Test
    void limitsLoginAttemptsPerIp() throws Exception {
        TrafficProtectionFilter filter = new TrafficProtectionFilter();

        for (int i = 0; i < 200; i++) {
            MockHttpServletResponse response = execute(filter, "/user/login", "POST", "203.0.113.8");
            assertThat(response.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse rejected = execute(filter, "/user/login", "POST", "203.0.113.8");
        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("1");
        assertThat(rejected.getContentAsString()).contains("访问过于频繁");

        MockHttpServletResponse otherIp = execute(filter, "/user/login", "POST", "203.0.113.9");
        assertThat(otherIp.getStatus()).isEqualTo(200);
    }

    @Test
    void ignoresUntrustedForwardedForAndUsesGatewayRealIp() throws Exception {
        TrafficProtectionFilter filter = new TrafficProtectionFilter();
        for (int i = 0; i < 200; i++) {
            MockHttpServletRequest request = request("/user/login", "POST", "198.51.100.4");
            request.addHeader("X-Forwarded-For", "10.0.0." + i);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }

        MockHttpServletRequest request = request("/user/login", "POST", "198.51.100.4");
        request.addHeader("X-Forwarded-For", "10.0.0.200");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void rendersFriendlyHtmlForDirectBrowserNavigation() throws Exception {
        TrafficProtectionFilter filter = new TrafficProtectionFilter();
        for (int i = 0; i < 200; i++) {
            execute(filter, "/user/login", "POST", "203.0.113.10");
        }

        MockHttpServletRequest request = request("/user/login", "POST", "203.0.113.10");
        request.addHeader("Sec-Fetch-Dest", "document");
        request.addHeader("Accept", "text/html");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).startsWith("text/html");
        assertThat(response.getContentAsString()).contains("当前访问人数较多");
        assertThat(response.getContentAsString()).doesNotContain("\"code\":429");
    }

    @Test
    void authStateRefreshDoesNotConsumeNormalReadQuota() throws Exception {
        TrafficProtectionFilter filter = new TrafficProtectionFilter();

        for (int i = 0; i < 40; i++) {
            MockHttpServletResponse response = execute(filter, "/user/me", "GET", "203.0.113.11");
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void internalServerSideFetchIsNotLimitedBySharedBucket() throws Exception {
        TrafficProtectionFilter filter = new TrafficProtectionFilter();

        // Next.js 容器直接访问后端（无 X-Real-IP，来源为私有网段）时，全站 SSR 请求
        // 会共用同一个令牌桶；这里必须确认它们不会被误判为同一个客户端而 429。
        for (int i = 0; i < 80; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/notice/detail/" + i);
            request.setRemoteAddr("172.20.0.3");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void publicCallerWithoutGatewayHeaderIsStillLimited() throws Exception {
        TrafficProtectionFilter filter = new TrafficProtectionFilter();

        for (int i = 0; i < 300; i++) {
            assertThat(executeWithoutRealIp(filter, "/notice/list", "GET", "203.0.113.30").getStatus()).isEqualTo(200);
        }

        int rejected = 0;
        for (int i = 0; i < 20; i++) {
            if (executeWithoutRealIp(filter, "/notice/list", "GET", "203.0.113.30").getStatus() == 429) rejected++;
        }
        assertThat(rejected).isGreaterThanOrEqualTo(5);
    }

    @Test
    void privateClientBehindGatewayIsStillLimitedPerRealIp() throws Exception {
        TrafficProtectionFilter filter = new TrafficProtectionFilter();

        // 校园网 NAT 客户端经网关访问时仍带 X-Real-IP，必须按真实 IP 限流，
        // 不能因为来源是私有网段就整体放行。
        for (int i = 0; i < 200; i++) {
            assertThat(execute(filter, "/user/login", "POST", "192.168.1.50").getStatus()).isEqualTo(200);
        }
        assertThat(execute(filter, "/user/login", "POST", "192.168.1.50").getStatus()).isEqualTo(429);
    }

    @Test
    void rejectsThirtyFirstConcurrentUpload() throws Exception {
        TrafficProtectionFilter filter = new TrafficProtectionFilter();
        CountDownLatch entered = new CountDownLatch(30);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(30);
        try {
            List<Future<Integer>> uploads = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                String ip = "203.0.113." + (21 + i);
                uploads.add(executor.submit(() -> blockingUpload(filter, ip, entered, release)));
            }
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();

            MockHttpServletRequest thirtyFirstRequest = request("/file/upload", "POST", "203.0.113.99");
            thirtyFirstRequest.setContentType("multipart/form-data; boundary=test");
            MockHttpServletResponse thirtyFirstResponse = new MockHttpServletResponse();
            filter.doFilter(thirtyFirstRequest, thirtyFirstResponse, new MockFilterChain());

            assertThat(thirtyFirstResponse.getStatus()).isEqualTo(503);
            assertThat(thirtyFirstResponse.getHeader("Retry-After")).isEqualTo("3");

            release.countDown();
            for (Future<Integer> upload : uploads) {
                assertThat(upload.get(3, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private int blockingUpload(TrafficProtectionFilter filter, String ip, CountDownLatch entered,
                               CountDownLatch release) throws Exception {
        MockHttpServletRequest request = request("/file/upload", "POST", ip);
        request.setContentType("multipart/form-data; boundary=test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("测试等待超时");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });
        return response.getStatus();
    }

    private MockHttpServletResponse execute(TrafficProtectionFilter filter, String path, String method, String ip)
            throws Exception {
        MockHttpServletRequest request = request(path, method, ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private MockHttpServletResponse executeWithoutRealIp(TrafficProtectionFilter filter, String path,
                                                         String method, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private MockHttpServletRequest request(String path, String method, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        request.addHeader("X-Real-IP", ip);
        return request;
    }
}
