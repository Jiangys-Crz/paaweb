package com.cwnupaa.backend.service.qqbot;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Service
public class BotRuntime {
    private static final Logger log=LoggerFactory.getLogger(BotRuntime.class);
    private final BotConfigService config;
    private final OneBotConnection connection;
    private final BotQueryService queries;
    private final BotTaskService tasks;
    private final BotDeliveryStore deliveries;
    private final BotScheduledImageService images;
    private final ThreadPoolExecutor queue=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),r->{Thread t=new Thread(r,"qq-bot-send");t.setDaemon(true);return t;});
    private final Map<String,Long> seen=new LinkedHashMap<>();
    private final Map<String,Long> recent=new LinkedHashMap<>();
    private final Map<String,Integer> userPending=new HashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean deliveryQueued=new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicBoolean polling=new java.util.concurrent.atomic.AtomicBoolean();
    private volatile String lastError;
    private long lastCleanup;
    public BotRuntime(BotConfigService config,OneBotConnection connection,BotQueryService queries,BotTaskService tasks,BotDeliveryStore deliveries,BotScheduledImageService images) {
        this.config=config;this.connection=connection;this.queries=queries;this.tasks=tasks;this.deliveries=deliveries;this.images=images;
    }
    @PostConstruct public void init() {connection.onEvent(this::onMessage);}
    public Map<String,Object> status() {
        Map<String,Object> s=connection.status();s.put("queueSize",queue.getQueue().size());s.put("scheduledTasks",tasks.list().size());
        if(lastError!=null)s.put("lastError",lastError);return s;
    }
    public void reconnect() {lastError=null;connection.restart();}
    public void onMessage(JsonNode e) {
        BotConfig c=config.get();
        if(!c.isEnabled() || !"message".equals(e.path("post_type").asText()))return;
        String type=e.path("message_type").asText();
        if(!Set.of("group","private").contains(type))return;
        String user=e.path("user_id").asText(),group="group".equals(type)?e.path("group_id").asText():null;
        if(!user.matches("[1-9][0-9]{0,14}") || user.equals(e.path("self_id").asText()) || user.equals(connection.selfId()))return;
        if(group!=null && (!group.matches("[1-9][0-9]{4,14}") || !c.getQueryGroups().isEmpty()&&!c.getQueryGroups().contains(group)))return;
        StringBuilder text=new StringBuilder();boolean addressed=false;
        JsonNode message=e.path("message");
        if(message.isArray()) {
            for(JsonNode part:message) {
                if("text".equals(part.path("type").asText())) text.append(part.path("data").path("text").asText());
                if("at".equals(part.path("type").asText()) && part.path("data").path("qq").asText().equals(e.path("self_id").asText()))addressed=true;
            }
        } else if(message.isTextual()) {
            String raw=message.asText();
            String mention="[CQ:at,qq="+e.path("self_id").asText()+"]";
            addressed=raw.contains(mention);text.append(raw.replace(mention,""));
        }
        String command=text.toString().trim();
        if(group!=null&&!addressed&&!queries.recognized(command))return;
        if(command.length()>256)command="help";
        String messageId=e.path("message_id").asText();
        String identity=type+":"+(group==null?user:group)+":"+user;
        long now=System.currentTimeMillis();
        synchronized(seen) {
            if(!messageId.isEmpty()&&seen.putIfAbsent(e.path("self_id").asText()+":"+messageId,now)!=null)return;
            trim(seen,4096);trim(recent,5000);
            if(now-recent.getOrDefault(identity,0L)<1500 || userPending.getOrDefault(identity,0)>=3)return;
            recent.put(identity,now);userPending.merge(identity,1,Integer::sum);
        }
        String request=command;
        try {
            queue.execute(()-> {
                try {
                    BotConfig current=config.get();
                    if(!current.isEnabled()||group!=null&&!current.getQueryGroups().isEmpty()&&!current.getQueryGroups().contains(group))return;
                    BotQueryService.BotReply reply;
                    try {reply=queries.reply(request);}catch(Exception error){reply=new BotQueryService.BotReply("查询暂不可用，请稍后重试。发送 help 或 帮助 查看命令用法。",null);}
                    var prefix=new ArrayList<Map<String,Object>>();
                    if(messageId.matches("-?[0-9]{1,20}"))prefix.add(segment("reply",Map.of("id",messageId)));
                    // OneBot's native at segment is group-only; private replies address the user as text.
                    prefix.add(group == null ? segment("text",Map.of("text","@"+user+" ")) : segment("at",Map.of("qq",user)));
                    sendReply(group,user,prefix,reply.text(),reply.image());
                } catch(Exception error){lastError="回复发送失败，请检查连接和群权限";}
                finally {synchronized(seen){userPending.computeIfPresent(identity,(k,n)->n<=1?null:n-1);}}
            });
        }catch(RejectedExecutionException error){synchronized(seen){userPending.computeIfPresent(identity,(k,n)->n<=1?null:n-1);}}
    }
    @Scheduled(fixedDelay=30000,initialDelay=12000)
    public void discover() {
        BotConfig c=config.get();
        if(!c.isEnabled()||!polling.compareAndSet(false,true))return;
        try {
            LocalDateTime now=LocalDateTime.now(BotTaskService.ZONE);
            for(BotTask t:tasks.due(now)) deliveries.enqueue("task:"+t.getId()+":"+t.getRevision()+":"+t.getDueAt(),t.getGroupId(),t.getId(),null,
                    new BotDeliveryStore.Payload(t.getContent(),t.getImages(),t.getDueAt(),t.getRevision()));
            if(!c.getPushGroups().isEmpty()&&(c.isPushOnline()||c.isPushOffline()||c.isPushTraining()||c.isPushBoard())) {
                for(var p:queries.pushes(now)) {
                    // Catch brief outages for 30 minutes, but never announce a missed historical reminder days later.
                    if(!c.allowsPush(p.category())||p.dueAt()==null||p.dueAt().isAfter(now)||p.dueAt().isBefore(now.minusMinutes(30)))continue;
                    for(String group:c.getPushGroups()) deliveries.enqueue("push:"+p.key()+":"+p.dueAt(),group,null,p.category(),new BotDeliveryStore.Payload(p.text(),List.of(),p.dueAt(),null));
                }
            }
            if(System.currentTimeMillis()-lastCleanup>3600000){deliveries.cleanup(now);lastCleanup=System.currentTimeMillis();}
        }catch(Exception e){lastError="推送任务检查失败，请确认已执行QQ机器人数据库迁移及Hydro接口配置";log.warn("QQ bot discovery failed ({})",e.getClass().getSimpleName());}
        finally {polling.set(false);}
    }
    @Scheduled(fixedDelay=2000,initialDelay=15000)
    public void deliver() {
        if(!config.get().isEnabled()||!connection.connected()||!deliveryQueued.compareAndSet(false,true))return;
        try {queue.execute(()-> {
            try {
                BotConfig c=config.get();if(!c.isEnabled())return;
                var d=deliveries.claim(LocalDateTime.now(BotTaskService.ZONE));if(d==null)return;
                if(!allowed(d,c)){deliveries.cancel(d);return;}
                try {
                    var segments=new ArrayList<Map<String,Object>>();
                    if(d.category()!=null)segments.add(segment("at",Map.of("qq","all")));
                    if(d.payload().text()!=null&&!d.payload().text().isBlank())segments.add(segment("text",Map.of("text",d.payload().text())));
                    for(String source:images.prepare(d.payload().images()))segments.add(segment("image",Map.of("file",source)));
                    delay(d.payload().text());
                    if(!allowed(d,config.get())){deliveries.cancel(d);return;}
                    connection.send(d.groupId(),null,segments);
                    deliveries.complete(d,LocalDateTime.now(BotTaskService.ZONE));lastError=null;
                }catch(Exception e){deliveries.fail(d,LocalDateTime.now(BotTaskService.ZONE));lastError="消息投递失败，正在等待重试";}
            }catch(Exception e){lastError="推送队列不可用，请检查数据库迁移";}
            finally{deliveryQueued.set(false);}
        });}catch(RejectedExecutionException e){deliveryQueued.set(false);}
    }
    private boolean allowed(BotDeliveryStore.Delivery d,BotConfig c) {
        if(!c.isEnabled())return false;
        if(d.taskId()!=null){
            BotTask t=tasks.find(d.taskId());
            return t!=null&&t.isEnabled()&&Objects.equals(t.getRevision(),d.payload().revision())&&t.getDueAt().equals(d.payload().occurrence())&&t.getGroupId().equals(d.groupId())
                    &&Objects.equals(t.getContent(),d.payload().text())&&Objects.equals(t.getImages(),d.payload().images());
        }
        return c.getPushGroups().contains(d.groupId())&&c.allowsPush(d.category())
                &&d.payload().occurrence().isAfter(LocalDateTime.now(BotTaskService.ZONE).minusMinutes(30));
    }
    private void sendReply(String group,String user,List<Map<String,Object>> prefix,String text,byte[] image) throws Exception {
        List<String> chunks=chunks(text==null?"":text,3500);
        for(int i=0;i<chunks.size();i++){
            var parts=new ArrayList<>(prefix);
            if(!chunks.get(i).isBlank())parts.add(segment("text",Map.of("text","\n"+chunks.get(i))));
            if(i==chunks.size()-1&&image!=null)parts.add(segment("image",Map.of("file","base64://"+Base64.getEncoder().encodeToString(image))));
            delay(chunks.get(i));
            BotConfig c=config.get();if(!c.isEnabled()||group!=null&&!c.getQueryGroups().isEmpty()&&!c.getQueryGroups().contains(group))return;
            connection.send(group,user,parts);
        }
    }
    private void delay(String text) throws InterruptedException {
        BotConfig c=config.get();
        Thread.sleep(Math.max(c.getMinReplyDelayMillis(),Math.min(c.getMaxReplyDelayMillis(),(text==null?0:text.codePointCount(0,text.length()))*(long)c.getTypingMillisPerChar())));
    }
    static List<String> chunks(String text,int max) {
        if(text.isEmpty())return List.of("");
        List<String> result=new ArrayList<>();
        for(int i=0;i<text.length();){int end=Math.min(i+max,text.length());if(end<text.length()&&Character.isHighSurrogate(text.charAt(end-1)))end--;result.add(text.substring(i,end));i=end;}
        return result;
    }
    static Map<String,Object> segment(String type,Map<String,?> data){return Map.of("type",type,"data",data);}
    private static void trim(Map<String,Long> map,int max){while(map.size()>max){var it=map.keySet().iterator();it.next();it.remove();}}
    @PreDestroy public void close(){queue.shutdownNow();}
}
