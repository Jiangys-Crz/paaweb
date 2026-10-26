package com.cwnupaa.backend.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.Serial;
import java.io.Serializable;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Issues short-lived, session-bound opaque tokens for certificate preview and download.
 * Resource IDs and identity data must never be accepted from a preview/download URL.
 */
@Service
public class CertificateAccessTokenService {
    private static final String SESSION_ATTRIBUTE = CertificateAccessTokenService.class.getName() + ".tickets";
    private static final long TOKEN_TTL_MILLIS = 10 * 60 * 1000L;
    private static final int MAX_SESSION_TICKETS = 200;

    private final SecureRandom secureRandom = new SecureRandom();

    public String issueForUser(HttpSession session, Long grantId, Long userId) {
        return issue(session, new AccessTicket(grantId, userId, null, null, null,
                AccessScope.USER, System.currentTimeMillis() + TOKEN_TTL_MILLIS));
    }

    public String issueForGuest(HttpSession session, Long grantId, String school, String numberId, String realName) {
        return issue(session, new AccessTicket(grantId, null, school, numberId, realName,
                AccessScope.GUEST, System.currentTimeMillis() + TOKEN_TTL_MILLIS));
    }

    public AccessTicket resolve(HttpSession session, String token, AccessScope expectedScope) {
        if (session == null || !StringUtils.hasText(token) || expectedScope == null) return null;
        synchronized (session) {
            Map<String, AccessTicket> tickets = getTickets(session, false);
            if (tickets == null) return null;
            long now = System.currentTimeMillis();
            tickets.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().expiresAt() < now);
            AccessTicket ticket = tickets.get(token);
            if (ticket == null || ticket.scope() != expectedScope) return null;
            return ticket;
        }
    }

    private String issue(HttpSession session, AccessTicket ticket) {
        if (session == null || ticket == null || ticket.grantId() == null) {
            throw new IllegalArgumentException("证书访问凭证参数不完整");
        }
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        synchronized (session) {
            Map<String, AccessTicket> tickets = getTickets(session, true);
            long now = System.currentTimeMillis();
            tickets.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().expiresAt() < now);
            if (tickets.size() >= MAX_SESSION_TICKETS) tickets.clear();
            tickets.put(token, ticket);
        }
        return token;
    }

    @SuppressWarnings("unchecked")
    private Map<String, AccessTicket> getTickets(HttpSession session, boolean create) {
        Object stored = session.getAttribute(SESSION_ATTRIBUTE);
        if (stored instanceof Map<?, ?>) return (Map<String, AccessTicket>) stored;
        if (!create) return null;
        Map<String, AccessTicket> tickets = new HashMap<>();
        session.setAttribute(SESSION_ATTRIBUTE, tickets);
        return tickets;
    }

    public enum AccessScope {
        USER,
        GUEST
    }

    public record AccessTicket(Long grantId, Long userId, String school, String numberId,
                               String realName, AccessScope scope, long expiresAt) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }
}
