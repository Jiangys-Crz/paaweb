package com.cwnupaa.backend.task;

import com.cwnupaa.backend.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

/** Bounded workers; no SMTP work on request threads or Spring's scheduler thread. */
@Component
public class EventSignupNotificationTask implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(EventSignupNotificationTask.class);
    private final JdbcTemplate jdbc;
    private final EmailService email;
    private final ExecutorService workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new SynchronousQueue<>(), runnable -> {
                Thread thread = new Thread(runnable, "signup-mail");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public EventSignupNotificationTask(JdbcTemplate jdbc, EmailService email) {
        this.jdbc = jdbc;
        this.email = email;
    }

    @Scheduled(fixedDelayString = "${event.signup.notification-poll-ms:1000}")
    public void dispatch() {
        for (int i = 0; i < 2; i++) {
            try {
                workers.execute(() -> {
                    try {
                        for (int j = 0; j < 20 && !Thread.currentThread().isInterrupted() && deliverOne(); j++) { }
                    } catch (Exception ex) {
                        log.error("赛事报名通知任务处理失败，将在下一轮重试 ({})", ex.getClass().getSimpleName());
                    }
                });
            } catch (RejectedExecutionException busy) {
                // Tasks remain in the database while both delivery workers are busy.
                break;
            }
        }
    }

    public boolean deliverOne() {
        // Claim in one short autocommit statement. A lease recovers work after a crash.
        // Older messages for the same recipient must finish before newer status messages.
        var claimed = jdbc.queryForList("""
                UPDATE event_signup_notification SET available_at = CURRENT_TIMESTAMP + INTERVAL '5 minutes', attempts = attempts + 1
                WHERE id = (SELECT n.id FROM event_signup_notification n
                    WHERE n.available_at <= CURRENT_TIMESTAMP
                    AND NOT EXISTS (SELECT 1 FROM event_signup_notification older WHERE older.email=n.email AND older.id<n.id)
                    ORDER BY n.id FOR UPDATE SKIP LOCKED LIMIT 1)
                RETURNING *
                """);
        if (claimed.isEmpty()) return false;
        var row = claimed.get(0);
        long id = ((Number) row.get("id")).longValue();
        int attempt = ((Number) row.get("attempts")).intValue();
        try {
            // Legacy messages have no verifiable signup identity. Discard them too.
            // No connection or signup lock is held during SMTP delivery.
            Integer current = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM event_signup_notification n
                    WHERE n.id=? AND n.attempts=? AND (
                        (n.signup_kind='personal' AND EXISTS (SELECT 1 FROM signup_personal s
                            WHERE s.signup_id=n.signup_id AND s.event_id=n.event_id AND s.status=n.signup_status))
                        OR (n.signup_kind='team' AND EXISTS (SELECT 1 FROM signup_team s
                            WHERE s.signup_id=n.signup_id AND s.event_id=n.event_id AND s.status=n.signup_status)))
                    """, Integer.class, id, attempt);
            if (current == null || current == 0) {
                jdbc.update("DELETE FROM event_signup_notification WHERE id=? AND attempts=?", id, attempt);
                return true;
            }
            email.sendEventSignupStatusToRecipient((String) row.get("email"), (String) row.get("recipient_name"),
                    (String) row.get("event_name"), (String) row.get("team_name"), (String) row.get("signup_status"), (String) row.get("reason"));
            jdbc.update("DELETE FROM event_signup_notification WHERE id=? AND attempts=?", id, attempt);
        } catch (Exception ex) {
            long retrySeconds = Math.min(3600, 30L << Math.min(attempt - 1, 7));
            jdbc.update("UPDATE event_signup_notification SET available_at=CURRENT_TIMESTAMP + (? * INTERVAL '1 second') WHERE id=? AND attempts=?", retrySeconds, id, attempt);
            log.warn("赛事报名通知发送失败，任务 {} 将重试（第 {} 次） ({})", id, attempt, ex.getClass().getSimpleName());
        }
        return true;
    }

    @Override public void destroy() { workers.shutdownNow(); }
}
