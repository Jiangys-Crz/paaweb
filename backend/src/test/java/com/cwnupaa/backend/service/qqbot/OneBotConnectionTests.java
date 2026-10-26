package com.cwnupaa.backend.service.qqbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Real RFC 6455 handshake and frames against an ephemeral loopback-only OneBot stub. */
class OneBotConnectionTests {
    private final ObjectMapper json = new ObjectMapper();
    private final BotConfigService configs = mock(BotConfigService.class);
    private LoopbackOneBotServer server;
    private OneBotConnection connection;
    private BotConfig config;

    @AfterEach
    void closeLocalConnections() throws Exception {
        if (connection != null) connection.disconnect();
        if (server != null) {
            server.close();
            server.assertHealthy();
        }
    }

    private void connect() throws Exception {
        // This credential exists only in memory for this local test and is never logged.
        String token = UUID.randomUUID().toString();
        server = new LoopbackOneBotServer(json, token);
        config = new BotConfig();
        config.setEnabled(true);
        config.setWsUrl(server.url());
        config.setAccessToken(token);
        config.setReconnectSeconds(1);
        when(configs.get()).thenAnswer(ignored -> config);
        connection = new OneBotConnection(configs, json);
        connection.restart();
        server.await(() -> connection.connected() && "100001".equals(connection.selfId()));
    }

    @Test
    void authenticatesOverRealWebSocketReceivesFragmentedEventsAndSendsStructuredMessages() throws Exception {
        connect();
        assertThat(server.authenticatedConnections()).isEqualTo(1);
        assertThat(connection.status().get("connecting")).isEqualTo(false);
        JsonNode self = (JsonNode) connection.status().get("self");
        assertThat(self.path("nickname").asText()).isEqualTo("Loopback OneBot");

        BlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();
        connection.onEvent(received::add);
        JsonNode event = json.readTree("""
                {"post_type":"message","message_type":"group","group_id":123456789,"user_id":567890123,
                 "message_id":77,"message":[{"type":"text","data":{"text":"近期线下赛"}}]}
                """);
        server.latest().sendFragmented(event);
        assertThat(received.poll(5, TimeUnit.SECONDS)).isEqualTo(event);

        List<Map<String, Object>> segments = List.of(
                Map.of("type", "at", "data", Map.of("qq", "567890123")),
                Map.of("type", "text", "data", Map.of("text", "文本必须保持原样 [CQ:at,qq=all]")),
                Map.of("type", "image", "data", Map.of("file", "base64://aW1hZ2U=")));
        connection.send("123456789", null, segments);
        Request group = server.request("send_group_msg");
        assertThat(group.value().path("params").path("group_id").asLong()).isEqualTo(123456789L);
        assertThat(group.value().path("params").has("user_id")).isFalse();
        assertThat(group.value().path("params").path("auto_escape").asBoolean()).isTrue();
        assertThat(group.value().path("params").path("message")).isEqualTo(json.valueToTree(segments));
        assertThat(group.value().path("echo").asText()).isNotBlank();

        connection.send(null, "567890123", List.of(segments.get(1)));
        Request direct = server.request("send_private_msg");
        assertThat(direct.value().path("params").path("user_id").asLong()).isEqualTo(567890123L);
        assertThat(direct.value().path("params").has("group_id")).isFalse();
        assertThat(direct.value().path("echo").asText()).isNotEqualTo(group.value().path("echo").asText());
        assertThat(received).isEmpty();
    }

    @Test
    void echoesCorrelateOutOfOrderRepliesAndOneFailedRequestDoesNotFailAnother() throws Exception {
        connect();
        var failure = connection.call("audit_failure", Map.of("id", 1));
        var success = connection.call("audit_success", Map.of("id", 2));
        Request failedRequest = server.request("audit_failure");
        Request successfulRequest = server.request("audit_success");
        assertThat(failedRequest.value().path("echo").asText()).isNotEqualTo(successfulRequest.value().path("echo").asText());

        // An unrelated or late echo is ignored and cannot consume another request's future.
        server.latest().send(json.createObjectNode().put("echo", "unknown-echo").put("status", "failed").put("retcode", 1400));
        server.reply(successfulRequest, true, json.createObjectNode().put("result", "second-request"));
        assertThat(success.get(5, TimeUnit.SECONDS).path("result").asText()).isEqualTo("second-request");
        assertThat(failure.isDone()).isFalse();
        server.reply(failedRequest, false, json.createObjectNode().put("private_detail", "upstream-private-error"));
        assertThatThrownBy(() -> failure.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("OneBot拒绝请求，请检查机器人群权限");
        assertThat(connection.connected()).isTrue();

        var subsequent = connection.call("audit_after_failure", Map.of());
        server.reply(server.request("audit_after_failure"), true, json.createObjectNode().put("healthy", true));
        assertThat(subsequent.get(5, TimeUnit.SECONDS).path("healthy").asBoolean()).isTrue();
    }

    @Test
    void droppedSocketFailsPendingCallsAndRestartReconnectsWithoutOldSessionState() throws Exception {
        connect();
        var pending = connection.call("audit_pending", Map.of());
        server.request("audit_pending");
        server.latest().close();
        assertThatThrownBy(() -> pending.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalStateException.class);
        server.await(() -> !connection.connected());
        assertThat(connection.selfId()).isEmpty();
        assertThat(connection.status().get("lastError")).isEqualTo("连接中断或握手失败，请检查地址、Token与NapCat状态");

        connection.restart();
        server.await(() -> connection.connected() && "100002".equals(connection.selfId()));
        assertThat(server.authenticatedConnections()).isEqualTo(2);
        assertThat(connection.status().get("lastError")).isNull();
        connection.send("123456789", null, List.of(Map.of("type", "text", "data", Map.of("text", "重新连接后的消息"))));
        assertThat(server.request("send_group_msg").session().number).isEqualTo(2);

        var reset = connection.call("audit_reset", Map.of());
        server.request("audit_reset");
        config.setEnabled(false);
        connection.maintain();
        assertThat(connection.connected()).isFalse();
        assertThat(connection.selfId()).isEmpty();
        assertThatThrownBy(() -> reset.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class).hasRootCauseMessage("连接已重置");
        connection.maintain();
        assertThat(server.authenticatedConnections()).isEqualTo(2);
        assertThat(connection.status().get("enabled")).isEqualTo(false);
    }

    private record Request(Session session, JsonNode value) {}

    private static final class Session implements AutoCloseable {
        private final Socket socket;
        private final OutputStream output;
        private final ObjectMapper json;
        private final int number;

        private Session(Socket socket, ObjectMapper json, int number) throws IOException {
            this.socket = socket;
            this.output = socket.getOutputStream();
            this.json = json;
            this.number = number;
        }

        synchronized void send(JsonNode value) throws IOException {
            frame(1, true, json.writeValueAsBytes(value));
        }

        synchronized void sendFragmented(JsonNode value) throws IOException {
            byte[] bytes = json.writeValueAsBytes(value);
            int split = bytes.length / 2;
            frame(1, false, java.util.Arrays.copyOfRange(bytes, 0, split));
            frame(0, true, java.util.Arrays.copyOfRange(bytes, split, bytes.length));
        }

        synchronized void frame(int opcode, boolean last, byte[] payload) throws IOException {
            output.write((last ? 0x80 : 0) | opcode);
            if (payload.length < 126) output.write(payload.length);
            else if (payload.length <= 65535) {
                output.write(126);
                output.write(payload.length >>> 8);
                output.write(payload.length & 255);
            } else {
                output.write(127);
                for (int shift = 56; shift >= 0; shift -= 8) output.write((int) ((long) payload.length >>> shift) & 255);
            }
            output.write(payload);
            output.flush();
        }

        @Override public void close() throws IOException { socket.close(); }
    }

    /** Minimal server used only for local tests; no third-party server or real QQ is involved. */
    private static final class LoopbackOneBotServer implements AutoCloseable {
        private final ServerSocket listener = new ServerSocket();
        private final ExecutorService workers = Executors.newCachedThreadPool();
        private final ObjectMapper json;
        private final String expectedAuthorization;
        private final AtomicInteger accepted = new AtomicInteger();
        private final List<Session> sessions = new CopyOnWriteArrayList<>();
        private final List<Socket> sockets = new CopyOnWriteArrayList<>();
        private final BlockingQueue<Request> requests = new LinkedBlockingQueue<>();
        private final BlockingQueue<Throwable> failures = new LinkedBlockingQueue<>();
        private volatile boolean stopped;

        private LoopbackOneBotServer(ObjectMapper json, String token) throws IOException {
            this.json = json;
            this.expectedAuthorization = "Bearer " + token;
            listener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            workers.submit(() -> {
                try {
                    while (!stopped) {
                        Socket socket = listener.accept();
                        sockets.add(socket);
                        workers.submit(() -> serve(socket));
                    }
                } catch (SocketException ignored) {
                    if (!stopped) failures.add(new AssertionError("Local listener stopped unexpectedly"));
                } catch (Exception error) { if (!stopped) failures.add(error); }
            });
        }

        String url() { return "ws://127.0.0.1:" + listener.getLocalPort() + "/onebot"; }
        int authenticatedConnections() { return accepted.get(); }
        Session latest() { return sessions.getLast(); }

        void await(BooleanSupplier condition) throws Exception {
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
                assertHealthy();
                Thread.sleep(10);
            }
            assertHealthy();
            assertThat(condition.getAsBoolean()).as("Local OneBot connection reaches the expected state").isTrue();
        }

        Request request(String action) throws InterruptedException {
            assertHealthy();
            Request request = requests.poll(5, TimeUnit.SECONDS);
            assertHealthy();
            assertThat(request).as("Local OneBot action " + action).isNotNull();
            assertThat(request.value().path("action").asText()).isEqualTo(action);
            return request;
        }

        void reply(Request request, boolean success, JsonNode data) throws IOException {
            var response = json.createObjectNode().put("echo", request.value().path("echo").asText())
                    .put("status", success ? "ok" : "failed").put("retcode", success ? 0 : 1400);
            response.set("data", data);
            request.session().send(response);
        }

        private void serve(Socket socket) {
            try (socket) {
                socket.setSoTimeout(10000);
                InputStream input = socket.getInputStream();
                String header = handshake(input);
                String[] lines = header.split("\r\n");
                if (!"GET /onebot HTTP/1.1".equals(lines[0])) throw new AssertionError("Unexpected local handshake path");
                Map<String, String> headers = new java.util.HashMap<>();
                for (int i = 1; i < lines.length; i++) {
                    int colon = lines[i].indexOf(':');
                    if (colon > 0) headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).trim());
                }
                // Do not include either actual or expected credential in assertion failures.
                if (!expectedAuthorization.equals(headers.get("authorization"))) throw new AssertionError("Local OneBot authentication was not valid");
                if (!"websocket".equalsIgnoreCase(headers.get("upgrade"))) throw new AssertionError("Missing WebSocket upgrade");
                String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                        .digest((headers.get("sec-websocket-key") + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.ISO_8859_1)));
                socket.getOutputStream().write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n\r\n")
                        .getBytes(StandardCharsets.ISO_8859_1));
                socket.getOutputStream().flush();
                Session session = new Session(socket, json, accepted.incrementAndGet());
                sessions.add(session);
                socket.setSoTimeout(0);
                ByteArrayOutputStream text = new ByteArrayOutputStream();
                while (!stopped) {
                    int first = input.read();
                    if (first == -1) return;
                    int second = octet(input);
                    if ((second & 0x80) == 0) throw new AssertionError("Client frames must be masked");
                    long length = second & 127;
                    if (length == 126) length = (octet(input) << 8) | octet(input);
                    else if (length == 127) {
                        length = 0;
                        for (int i = 0; i < 8; i++) length = (length << 8) | octet(input);
                    }
                    if (length < 0 || length > 131072) throw new AssertionError("Unexpected local frame size");
                    byte[] mask = exact(input, 4), payload = exact(input, (int) length);
                    for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
                    int opcode = first & 15;
                    if (opcode == 8) { session.frame(8, true, payload); return; }
                    if (opcode == 9) { session.frame(10, true, payload); continue; }
                    if (opcode == 10) continue;
                    if (opcode != 1 && opcode != 0) throw new AssertionError("Unexpected local frame opcode");
                    text.write(payload);
                    if ((first & 0x80) == 0) continue;
                    JsonNode value = json.readTree(text.toByteArray());
                    text.reset();
                    Request request = new Request(session, value);
                    switch (value.path("action").asText()) {
                        case "get_login_info" -> reply(request, true, json.createObjectNode().put("user_id", 100000 + session.number).put("nickname", "Loopback OneBot"));
                        case "send_group_msg", "send_private_msg" -> {
                            requests.add(request);
                            reply(request, true, json.createObjectNode().put("message_id", 9000 + session.number));
                        }
                        default -> requests.add(request);
                    }
                }
            } catch (SocketException | EOFException ignored) {
                // Client aborts and intentional lifecycle tests close sockets without a close frame.
            } catch (Throwable error) {
                if (!stopped) failures.add(error);
            }
        }

        private static String handshake(InputStream input) throws IOException {
            ByteArrayOutputStream header = new ByteArrayOutputStream();
            int matched = 0;
            byte[] end = {'\r', '\n', '\r', '\n'};
            while (header.size() < 16384) {
                int value = octet(input);
                header.write(value);
                matched = value == end[matched] ? matched + 1 : value == '\r' ? 1 : 0;
                if (matched == end.length) return header.toString(StandardCharsets.ISO_8859_1);
            }
            throw new IOException("Local handshake header was too large");
        }

        private static int octet(InputStream input) throws IOException {
            int value = input.read();
            if (value == -1) throw new EOFException();
            return value;
        }

        private static byte[] exact(InputStream input, int size) throws IOException {
            byte[] value = input.readNBytes(size);
            if (value.length != size) throw new EOFException();
            return value;
        }

        void assertHealthy() {
            Throwable failure = failures.poll();
            if (failure != null) throw new AssertionError("Local OneBot stub failed", failure);
        }

        @Override public void close() throws Exception {
            stopped = true;
            listener.close();
            for (Socket socket : sockets) socket.close();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).as("Local sockets and workers close promptly").isTrue();
        }
    }
}
