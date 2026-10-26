package com.cwnupaa.backend.service.qqbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
public class BotDeliveryStore {
    public record Payload(String text,List<String> images,LocalDateTime occurrence,String revision) {}
    public record Delivery(String key,String groupId,String taskId,String category,Payload payload,int attempts) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final BotTaskService tasks;
    public BotDeliveryStore(JdbcTemplate jdbc,ObjectMapper json,BotTaskService tasks) {this.jdbc=jdbc;this.json=json;this.tasks=tasks;}
    public void enqueue(String key,String group,String task,String category,Payload payload) {
        try {
            jdbc.update("INSERT INTO qq_bot_delivery(delivery_key,group_id,task_id,category,payload) VALUES(?,?,?,?,?) ON CONFLICT(delivery_key) DO NOTHING",
                    hash(key+":"+group),group,task,category,json.writeValueAsString(payload));
        } catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
    }
    @Transactional
    public Delivery claim(LocalDateTime now) {
        var rows=jdbc.query("SELECT * FROM qq_bot_delivery WHERE (status='pending' AND available_at<=?) OR (status='sending' AND lease_until<?) ORDER BY available_at LIMIT 20",
            (rs,n)->new Delivery(rs.getString("delivery_key"),rs.getString("group_id"),rs.getString("task_id"),rs.getString("category"),decode(rs.getString("payload")),rs.getInt("attempts")),now,now);
        for(Delivery d:rows) {
            int changed=jdbc.update("UPDATE qq_bot_delivery SET status='sending',lease_until=?,attempts=attempts+1 WHERE delivery_key=? AND ((status='pending' AND available_at<=?) OR (status='sending' AND lease_until<?))",
                    now.plusMinutes(3),d.key(),now,now);
            if(changed==1) return new Delivery(d.key(),d.groupId(),d.taskId(),d.category(),d.payload(),d.attempts()+1);
        }
        return null;
    }
    @Transactional
    public void complete(Delivery d,LocalDateTime now) {
        int changed=jdbc.update("UPDATE qq_bot_delivery SET status='sent',sent_at=?,lease_until=NULL,last_error=NULL WHERE delivery_key=? AND status='sending' AND attempts=?",now,d.key(),d.attempts());
        if(changed==1 && d.taskId()!=null) tasks.delivered(d.taskId(),d.payload().revision(),d.payload().occurrence(),now,null);
    }
    @Transactional
    public void fail(Delivery d,LocalDateTime now) {
        String error="发送失败，请检查机器人连接、群成员身份和@全体权限";
        int changed=jdbc.update("UPDATE qq_bot_delivery SET status='pending',available_at=?,lease_until=NULL,last_error=? WHERE delivery_key=? AND status='sending' AND attempts=?",
                now.plusSeconds(Math.min(3600,30L*(1L<<Math.min(d.attempts()-1,7)))),error,d.key(),d.attempts());
        if(changed==1 && d.taskId()!=null) tasks.delivered(d.taskId(),d.payload().revision(),d.payload().occurrence(),now,error);
    }
    public void cancel(Delivery d) {jdbc.update("UPDATE qq_bot_delivery SET status='cancelled',lease_until=NULL WHERE delivery_key=? AND status='sending' AND attempts=?",d.key(),d.attempts());}
    public void cleanup(LocalDateTime now) {
        jdbc.update("DELETE FROM qq_bot_delivery WHERE status IN ('sent','cancelled') AND created_at<?",now.minusDays(90));
    }
    private Payload decode(String s) {try{return json.readValue(s,Payload.class);}catch(Exception e){throw new IllegalStateException("推送数据异常",e);}}
    static String hash(String value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
}
