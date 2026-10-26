package com.cwnupaa.backend.service.qqbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** OneBot 11 forward WebSocket. Only this service can select protocol actions. */
@Component
public class OneBotConnection {
    private final BotConfigService config;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ConcurrentMap<String,CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private volatile WebSocket socket;
    private volatile boolean connecting;
    private volatile String lastError;
    private volatile Instant connectedAt;
    private volatile long lastAttempt;
    private volatile long generation;
    private volatile JsonNode self;
    private volatile Consumer<JsonNode> receiver = event -> {};
    private CompletableFuture<?> writes = CompletableFuture.completedFuture(null);
    public OneBotConnection(BotConfigService config,ObjectMapper json) { this.config=config;this.json=json; }
    public void onEvent(Consumer<JsonNode> listener) { receiver=listener; }
    public boolean connected() { return socket != null && !socket.isOutputClosed(); }
    public String selfId() { return self == null ? "" : self.path("user_id").asText(); }
    public Map<String,Object> status() {
        Map<String,Object> s=new LinkedHashMap<>();
        s.put("connected",connected());s.put("connecting",connecting);s.put("enabled",config.get().isEnabled());
        s.put("lastConnectedAt",connectedAt);s.put("lastError",lastError);s.put("self",self);return s;
    }
    @Scheduled(fixedDelay=5000, initialDelay=5000)
    public void maintain() {
        BotConfig c=config.get();
        if(!c.isEnabled()) { if(connected() || connecting) disconnect();return; }
        if(connected() || connecting || System.currentTimeMillis()-lastAttempt < c.getReconnectSeconds()*1000L) return;
        connect(c);
    }
    public synchronized void restart() { disconnect();lastAttempt=0;if(config.get().isEnabled()) connect(config.get()); }
    private synchronized void connect(BotConfig c) {
        if(connecting || connected()) return;
        connecting=true;lastAttempt=System.currentTimeMillis();long epoch=++generation;
        try {
            BotConfigService.validate(c);
            http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10))
                .header("Authorization","Bearer "+c.getAccessToken())
                .buildAsync(BotValidation.endpoint(c.getWsUrl(),true),new WebSocket.Listener() {
                    private final StringBuilder buffer=new StringBuilder();
                    @Override public void onOpen(WebSocket ws) {
                        synchronized(OneBotConnection.this) {
                            if(epoch!=generation) { ws.abort();return; }
                            socket=ws;connecting=false;connectedAt=Instant.now();lastError=null;
                        }
                        ws.request(1);
                        call("get_login_info",Map.of()).thenAccept(data -> {
                            if(epoch==generation) self=json.createObjectNode().put("user_id",data.path("user_id").asText()).put("nickname",data.path("nickname").asText());
                        }).exceptionally(e->null);
                    }
                    @Override public CompletionStage<?> onText(WebSocket ws,CharSequence data,boolean last) {
                        if(epoch!=generation) return null;
                        if(buffer.length()+data.length()>131072) { buffer.setLength(0);ws.abort();closed(epoch);return null; }
                        buffer.append(data);
                        if(last) {
                            try { handle(json.readTree(buffer.toString())); } catch(Exception ignored) {} finally { buffer.setLength(0); }
                        }
                        ws.request(1);return null;
                    }
                    @Override public CompletionStage<?> onClose(WebSocket ws,int code,String reason) { closed(epoch);return null; }
                    @Override public void onError(WebSocket ws,Throwable error) { closed(epoch); }
                }).orTimeout(15,TimeUnit.SECONDS).whenComplete((ws,e)-> {
                    if(e!=null) closed(epoch);
                });
        } catch(Exception e) { closed(epoch); }
    }
    private void handle(JsonNode event) {
        String echo=event.path("echo").asText("");
        var future=pending.remove(echo);
        if(future!=null) {
            if(event.path("retcode").asInt(-1)==0 && !"failed".equals(event.path("status").asText())) future.complete(event.path("data"));
            else future.completeExceptionally(new IllegalStateException("OneBot拒绝请求，请检查机器人群权限"));
        } else if("message".equals(event.path("post_type").asText())) receiver.accept(event);
    }
    private synchronized void closed(long epoch) {
        if(epoch!=generation) return;
        socket=null;connecting=false;self=null;lastError="连接中断或握手失败，请检查地址、Token与NapCat状态";
        pending.values().forEach(f->f.completeExceptionally(new IllegalStateException("OneBot连接已断开")));pending.clear();
    }
    public CompletableFuture<JsonNode> call(String action,Object params) {
        WebSocket ws=socket;
        if(ws==null || ws.isOutputClosed()) return CompletableFuture.failedFuture(new IllegalStateException("OneBot尚未连接"));
        String echo=UUID.randomUUID().toString();
        var future=new CompletableFuture<JsonNode>();pending.put(echo,future);
        future.orTimeout(15,TimeUnit.SECONDS).whenComplete((r,e)->pending.remove(echo));
        try {
            write(ws,json.writeValueAsString(Map.of("action",action,"params",params,"echo",echo)))
                    .whenComplete((r,e)-> { if(e!=null) future.completeExceptionally(e); });
        } catch(Exception e) { future.completeExceptionally(e); }
        return future;
    }
    private synchronized CompletableFuture<WebSocket> write(WebSocket ws,String value) {
        CompletableFuture<WebSocket> next=writes.handle((r,e)->null).thenCompose(ignored->ws.sendText(value,true));
        writes=next;return next;
    }
    public void send(String group,String user,List<Map<String,Object>> message) throws Exception {
        Map<String,Object> p=new LinkedHashMap<>();
        p.put(group==null?"user_id":"group_id",Long.parseLong(group==null?user:group));
        p.put("message",message);p.put("auto_escape",true);
        call(group==null?"send_private_msg":"send_group_msg",p).get(17,TimeUnit.SECONDS);
    }
    @PreDestroy public synchronized void disconnect() {
        generation++;WebSocket ws=socket;socket=null;connecting=false;self=null;
        if(ws!=null) ws.abort();
        pending.values().forEach(f->f.completeExceptionally(new IllegalStateException("连接已重置")));pending.clear();
    }
}
