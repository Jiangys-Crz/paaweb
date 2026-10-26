package com.cwnupaa.backend.service.qqbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HydroBotClientTests {
    private HttpServer server;
    private final BotConfigService configs = mock(BotConfigService.class);

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void profileAuthenticatesAndEncodesNormalizedStoredEmail() throws Exception {
        AtomicReference<String> query = new AtomicReference<>(), key = new AtomicReference<>();
        HydroBotClient client = server(200, """
                {"rating":1675,"maxRating":1800,"ratedCount":8,"level":"Expert","color":"#3b82f6",
                 "solvedCount":262,"difficulty":[{"label":"800+","count":100,"percent":52.3}]}
                """, query, key);
        var profile = client.profile(" Alice+Tag@Example.com ");
        assertThat(profile.rating()).isEqualTo(1675);
        assertThat(profile.ratedCount()).isEqualTo(8);
        assertThat(profile.difficulty()).singleElement().satisfies(d -> assertThat(d.percent()).isEqualTo(52.3));
        assertThat(query.get()).isEqualTo("email=alice%2Btag%40example.com");
        assertThat(URLDecoder.decode(query.get(), StandardCharsets.UTF_8)).isEqualTo("email=alice+tag@example.com");
        assertThat(key.get()).isEqualTo("private-api-key");
    }

    @Test void missingOjAccountGetsFirstLoginGuidanceAndNoSecrets() throws Exception {
        HydroBotClient client = server(404, "{\"error\":\"not_found\"}", new AtomicReference<>(), new AtomicReference<>());
        assertThatThrownBy(() -> client.profile("alice@example.com"))
                .isInstanceOf(HydroBotClient.HydroUnavailableException.class)
                .hasMessageContainingAll("尚未在配置的 OJ 创建账号", "/rating 用户名")
                .hasMessageNotContainingAny("private-api-key", "alice@example.com");
    }

    @Test void noWebsiteEmailIsDifferentFromMissingOjAccount() {
        HydroBotClient client = new HydroBotClient(configs, new ObjectMapper());
        assertThatThrownBy(() -> client.profile(" ")).hasMessageContaining("官网账号尚未绑定邮箱");
        verifyNoInteractions(configs);
    }

    @Test void refusesRedirectsAndSuppressesRemoteErrorBodies() throws Exception {
        HydroBotClient client = server(302, "private-api-key upstream host internal.example", new AtomicReference<>(), new AtomicReference<>());
        assertThatThrownBy(() -> client.profile("alice@example.com"))
                .hasMessageContaining("暂时不可用").hasMessageNotContainingAny("private-api-key", "internal.example");
    }

    @Test void completeBodyDeadlineCancelsServerThatSendsHeadersThenStalls() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/rating-system/bot/profile", exchange -> {
            exchange.sendResponseHeaders(200, 1000);
            exchange.getResponseBody().write('{'); exchange.getResponseBody().flush();
            try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        BotConfig config = new BotConfig(); config.setHydroApiKey("private-api-key");
        config.setHydroBaseUrl("http://127.0.0.1:" + server.getAddress().getPort()); when(configs.get()).thenReturn(config);
        HydroBotClient client = new HydroBotClient(configs, new ObjectMapper(), Duration.ofMillis(300));
        long started = System.nanoTime();
        try {
            assertThatThrownBy(() -> client.profile("alice@example.com")).hasMessageContaining("暂时不可用");
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test void oversizedBodyIsRejectedBeforeJsonParsing() throws Exception {
        HydroBotClient client = server(200, " ".repeat(2 * 1024 * 1024 + 1), new AtomicReference<>(), new AtomicReference<>());
        assertThatThrownBy(() -> client.profile("alice@example.com")).hasMessageContaining("暂时不可用");
    }

    @Test void contestDatesConvertUtcToShanghaiAndUrlsUseConfiguredDeployment() throws Exception {
        HydroBotClient client = server(200, """
                {"contests":[{"id":"abcdef","title":"OJ 周赛","beginAt":"2026-10-27T04:00:00.000Z",
                "endAt":"2026-10-27T08:00:00.000Z","registrationEnd":"2026-10-27T08:00:00.000Z",
                "url":"http://127.0.0.1/admin","rated":true}]}
                """, new AtomicReference<>(), new AtomicReference<>());
        assertThat(client.contests()).singleElement().satisfies(c -> {
            assertThat(c.beginAt()).isEqualTo(LocalDateTime.of(2026, 10, 27, 12, 0));
            assertThat(c.url()).isEqualTo(configs.get().getHydroBaseUrl() + "/contest/abcdef");
            assertThat(c.registrationStart()).isNull();
        });
    }

    private HydroBotClient server(int status, String body, AtomicReference<String> query, AtomicReference<String> key) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rating-system/bot/", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery()); key.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (status == 302) exchange.getResponseHeaders().set("Location", "http://127.0.0.1:1/private");
            exchange.sendResponseHeaders(status, response.length);
            try (var out = exchange.getResponseBody()) { out.write(response); }
        });
        server.start();
        BotConfig config = new BotConfig(); config.setHydroApiKey("private-api-key");
        config.setHydroBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        when(configs.get()).thenReturn(config);
        return new HydroBotClient(configs, new ObjectMapper());
    }
}
