package com.cwnupaa.backend.service.qqbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** The bot has exactly two read-only Hydro capabilities; user input never selects a URL or method. */
@Service
public class HydroBotClient {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final int MAX_RESPONSE = 2 * 1024 * 1024;
    private final BotConfigService configService;
    private final ObjectMapper json;
    private final Duration requestTimeout;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @Autowired
    public HydroBotClient(BotConfigService configService, ObjectMapper json) {
        this(configService, json, Duration.ofSeconds(10));
    }

    HydroBotClient(BotConfigService configService, ObjectMapper json, Duration requestTimeout) {
        this.configService = configService;
        this.json = json;
        this.requestTimeout = requestTimeout;
    }

    public List<HydroContest> contests() {
        JsonNode root = get("/rating-system/bot/contests", null);
        List<HydroContest> result = new ArrayList<>();
        for (JsonNode item : root.path("contests")) {
            LocalDateTime start = date(item.get("beginAt"));
            LocalDateTime end = date(item.get("endAt"));
            String id = text(item, "id", "");
            if (!id.matches("[a-zA-Z0-9_-]{1,100}") || start == null || end == null || end.isBefore(start)) continue;
            // Build the public URL from the known domain rather than trusting a remote URL field.
            result.add(new HydroContest(id, text(item, "title", "未命名比赛"), start, end,
                    date(item.get("registrationStart")), date(item.get("registrationEnd")),
                    configService.get().getHydroBaseUrl().replaceAll("/+$", "") + "/contest/" + id, item.path("rated").asBoolean(false)));
        }
        return result;
    }

    public HydroProfile profile(String email) {
        if (email == null || email.isBlank() || email.length() > 254) throw new HydroUnavailableException("官网账号尚未绑定邮箱。");
        JsonNode root = get("/rating-system/bot/profile", email.strip().toLowerCase(Locale.ROOT));
        if (!root.path("rating").isNumber()) throw new HydroUnavailableException("协会 OJ 返回的资料不完整，请稍后重试。");
        List<Difficulty> difficulty = new ArrayList<>();
        for (JsonNode item : root.path("difficulty")) {
            if (difficulty.size() >= 16) break;
            difficulty.add(new Difficulty(text(item, "label", "未分级"), Math.max(0, item.path("count").asInt()),
                    Math.max(0, Math.min(100, item.path("percent").asDouble()))));
        }
        return new HydroProfile(root.path("rating").asInt(), root.path("maxRating").asInt(),
                Math.max(0, root.path("ratedCount").asInt()), text(root, "level", "Unrated"),
                text(root, "color", "#64748b"), Math.max(0, root.path("solvedCount").asInt()), difficulty);
    }

    private JsonNode get(String path, String email) {
        BotConfig config = configService.get();
        String key = config.getHydroApiKey();
        if (key == null || key.isBlank()) throw new HydroUnavailableException("协会 OJ 查询尚未配置，请联系管理员。");
        try {
            URI base = URI.create(config.getHydroBaseUrl());
            if (!List.of("https", "http").contains(base.getScheme()) || base.getHost() == null
                    || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
                throw new IllegalArgumentException("Invalid Hydro base URL");
            }
            String endpoint = base.toString().replaceAll("/+$", "") + path;
            if (email != null) endpoint += "?email=" + java.net.URLEncoder.encode(email, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(requestTimeout)
                    .header("Accept", "application/json").header("X-API-Key", key).GET().build();
            CompletableFuture<HttpResponse<byte[]>> pending = http.sendAsync(request, SafeHttpBody.limited(MAX_RESPONSE));
            HttpResponse<byte[]> response;
            try {
                // Time out a separate stage so cancellation still reaches the original HTTP exchange.
                response = pending.copy().orTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
                        .get(requestTimeout.toMillis() + 1000, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                pending.cancel(true);
                throw e;
            }
            if (response.statusCode() == 404 && email != null) throw new HydroUnavailableException("该账号尚未在配置的 OJ 创建账号，请先在 OJ 登录一次，再重试 /rating 用户名。");
            if (response.statusCode() != 200) throw new HydroUnavailableException("协会 OJ 查询暂时不可用，请稍后重试。");
            JsonNode result = json.readTree(response.body());
            if (result == null || !result.isObject()) throw new IllegalStateException("Invalid Hydro response");
            return result;
        } catch (HydroUnavailableException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HydroUnavailableException("协会 OJ 查询已中断，请稍后重试。");
        } catch (Exception e) {
            // Never expose the API key, email, response body, or internal network details to QQ.
            throw new HydroUnavailableException("协会 OJ 查询暂时不可用，请稍后重试。");
        }
    }

    static LocalDateTime date(JsonNode value) {
        if (value == null || value.isNull()) return null;
        try {
            if (value.isNumber()) {
                long epoch = value.asLong();
                return LocalDateTime.ofInstant(Math.abs(epoch) < 100_000_000_000L ? Instant.ofEpochSecond(epoch) : Instant.ofEpochMilli(epoch), SHANGHAI);
            }
            String s = value.asText();
            try { return LocalDateTime.ofInstant(Instant.parse(s), SHANGHAI); }
            catch (Exception ignored) { return LocalDateTime.parse(s.replace(' ', 'T')); }
        } catch (Exception ignored) { return null; }
    }

    private static String text(JsonNode value, String field, String fallback) {
        JsonNode node = value.get(field);
        return node == null || node.isNull() ? fallback : node.asText(fallback);
    }

    public record HydroContest(String id, String title, LocalDateTime beginAt, LocalDateTime endAt,
                               LocalDateTime registrationStart, LocalDateTime registrationEnd, String url, boolean rated) {}
    public record Difficulty(String label, int count, double percent) {}
    public record HydroProfile(int rating, int maxRating, int ratedCount, String level, String color,
                               int solvedCount, List<Difficulty> difficulty) {}
    public static class HydroUnavailableException extends RuntimeException {
        public HydroUnavailableException(String message) { super(message); }
    }
}
