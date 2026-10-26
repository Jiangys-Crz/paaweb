package com.cwnupaa.backend.service.qqbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class BotTaskService {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public BotTaskService(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    public List<BotTask> list() {
        return jdbc.query("SELECT payload FROM qq_bot_task ORDER BY due_at,id", (rs,n) -> decode(rs.getString(1)));
    }
    public BotTask find(String id) {
        return jdbc.query("SELECT payload FROM qq_bot_task WHERE id=?", (rs,n) -> decode(rs.getString(1)), id).stream().findFirst().orElse(null);
    }
    @Transactional
    public BotTask save(String id, BotTask task) {
        if (id != null && find(id) == null) throw new IllegalArgumentException("定时任务不存在");
        if (id == null && jdbc.queryForObject("SELECT COUNT(*) FROM qq_bot_task", Integer.class) >= 500)
            throw new IllegalArgumentException("最多保存500条定时任务");
        validate(task);
        task.setId(id == null ? UUID.randomUUID().toString() : id);
        task.setRevision(UUID.randomUUID().toString());
        BotTask previous = id == null ? null : find(id);
        task.setLastSentAt(previous == null ? null : previous.getLastSentAt()); task.setLastError(null);
        if (id != null) jdbc.update("DELETE FROM qq_bot_delivery WHERE task_id=? AND status='pending'",id);
        jdbc.update("INSERT INTO qq_bot_task(id,payload,due_at,enabled) VALUES(?,?,?,?) "
                + "ON CONFLICT(id) DO UPDATE SET payload=EXCLUDED.payload,due_at=EXCLUDED.due_at,enabled=EXCLUDED.enabled,updated_at=CURRENT_TIMESTAMP",
                task.getId(), encode(task), task.getDueAt(), task.isEnabled());
        return task;
    }
    @Transactional
    public void delete(String id) {
        jdbc.update("DELETE FROM qq_bot_delivery WHERE task_id=? AND status='pending'",id);
        jdbc.update("DELETE FROM qq_bot_task WHERE id=?",id);
    }
    public List<BotTask> due(LocalDateTime now) {
        return jdbc.query("SELECT payload FROM qq_bot_task WHERE enabled=TRUE AND due_at<=? ORDER BY due_at LIMIT 100",(rs,n)->decode(rs.getString(1)),now);
    }
    public void delivered(String id, String revision, LocalDateTime occurrence, LocalDateTime now, String error) {
        // Lock the task so an administrator's concurrent edit cannot be overwritten by delivery completion.
        var rows = jdbc.query("SELECT payload FROM qq_bot_task WHERE id=? FOR UPDATE",(rs,n)->decode(rs.getString(1)),id);
        if (rows.isEmpty()) return;
        BotTask task = rows.getFirst();
        if (!Objects.equals(task.getRevision(), revision) || !task.getDueAt().equals(occurrence)) return;
        task.setLastError(error);
        if (error == null) {
            task.setLastSentAt(now);
            LocalDateTime next = next(task, now);
            if (next == null) task.setEnabled(false); else task.setDueAt(next);
        }
        jdbc.update("UPDATE qq_bot_task SET payload=?,due_at=?,enabled=? WHERE id=?",encode(task),task.getDueAt(),task.isEnabled(),id);
    }
    public static LocalDateTime next(BotTask task, LocalDateTime now) {
        long minutes = switch(task.getRecurrence()) {
            case "DAILY" -> 1440;
            case "WEEKLY" -> 10080;
            case "INTERVAL" -> task.getIntervalMinutes();
            default -> 0;
        };
        if (minutes == 0) return null;
        long steps = Math.max(1, Duration.between(task.getDueAt(),now).toMinutes() / minutes + 1);
        return task.getDueAt().plusMinutes(steps * minutes);
    }
    public static void validate(BotTask task) {
        BotValidation.group(task.getGroupId());
        if (task.getTitle() == null || task.getTitle().isBlank() || task.getTitle().length()>100)
            throw new IllegalArgumentException("任务标题需为1–100字");
        if (task.getContent() == null) task.setContent("");
        if (task.getContent().length()>5000) throw new IllegalArgumentException("消息最多5000字");
        if (task.getImages() == null) task.setImages(new ArrayList<>());
        if (task.getImages().size()>5) throw new IllegalArgumentException("每条消息最多5张图片");
        if (task.getContent().isBlank() && task.getImages().isEmpty()) throw new IllegalArgumentException("请填写文本或图片");
        task.getImages().forEach(BotValidation::publicImage);
        if (task.getDueAt() == null) throw new IllegalArgumentException("请选择首次发送时间（北京时间）");
        if (task.getRecurrence() == null || !Set.of("ONCE","DAILY","WEEKLY","INTERVAL").contains(task.getRecurrence())) throw new IllegalArgumentException("循环方式不合法");
        if ("INTERVAL".equals(task.getRecurrence())) BotValidation.range(task.getIntervalMinutes()==null?0:task.getIntervalMinutes(),1,525600,"循环间隔分钟");
    }
    private BotTask decode(String s) { try { return json.readValue(s,BotTask.class); } catch(Exception e) { throw new IllegalStateException("定时任务数据异常",e); } }
    private String encode(BotTask t) { try { return json.writeValueAsString(t); } catch(Exception e) { throw new IllegalStateException(e); } }
}
