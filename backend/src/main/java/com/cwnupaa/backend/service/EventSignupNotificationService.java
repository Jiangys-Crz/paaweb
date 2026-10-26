package com.cwnupaa.backend.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Map;

/** The notification and registration commit together. SMTP runs outside that transaction. */
@Service
public class EventSignupNotificationService {
    private final JdbcTemplate jdbc;

    public EventSignupNotificationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Long eventId, String signupKind, Long signupId, Map<String, String> targets,
                        String eventName, String teamName, String status, String reason) {
        // Only the latest state is useful to a recipient. Also removes claimed older
        // work; a worker checks that its claim still exists immediately before sending.
        invalidate(signupKind, signupId);
        var rows = new ArrayList<Object[]>();
        targets.forEach((email, name) -> {
            if (StringUtils.hasText(email)) rows.add(new Object[]{eventId, signupKind, signupId, email, name, eventName, teamName, status, reason});
        });
        if (!rows.isEmpty()) {
            jdbc.batchUpdate("INSERT INTO event_signup_notification(event_id,signup_kind,signup_id,email,recipient_name,event_name,team_name,signup_status,reason) VALUES (?,?,?,?,?,?,?,?,?)", rows);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void invalidate(String signupKind, Long signupId) {
        jdbc.update("DELETE FROM event_signup_notification WHERE signup_kind=? AND signup_id=?", signupKind, signupId);
    }
}
