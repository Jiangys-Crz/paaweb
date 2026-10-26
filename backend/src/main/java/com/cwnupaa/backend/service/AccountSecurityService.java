package com.cwnupaa.backend.service;

import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.AccountSecurityMapper;
import com.cwnupaa.backend.mapper.UserMapper;
import com.cwnupaa.backend.vo.UserProfileVO;
import com.cwnupaa.backend.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

/** 数据库版本使多实例部署和已有会话统一撤销，不依赖某一进程的会话列表。 */
@Service
public class AccountSecurityService {
    public static final String AUTH_VERSION_ATTRIBUTE = "accountAuthVersion";
    private final UserMapper users;
    private final AccountSecurityMapper security;

    public AccountSecurityService(UserMapper users, AccountSecurityMapper security) {
        this.users = users;
        this.security = security;
    }

    public Users validateSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("user") instanceof UserProfileVO snapshot)) return null;
        Users user = users.selectById(snapshot.getUserId());
        Object version = session.getAttribute(AUTH_VERSION_ATTRIBUTE);
        if (user == null || !SecurityUtils.isAllowedRole(user.getRole())
                || !(version instanceof Number) || ((Number) version).longValue() != versionOf(user)) {
            session.invalidate();
            request.removeAttribute("user");
            request.removeAttribute("userId");
            return null;
        }
        return user;
    }

    public HttpSession startSession(HttpServletRequest request, Users user, UserProfileVO profile) {
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute("user", profile);
        session.setAttribute(AUTH_VERSION_ATTRIBUTE, versionOf(user));
        session.setMaxInactiveInterval(2592000);
        return session;
    }

    public void revokeSessions(Users target, HttpServletRequest currentRequest, boolean retainCurrent) {
        Long version = security.incrementAuthVersion(target.getUserId());
        if (version == null) throw new IllegalStateException("用户不存在，无法更新会话安全状态");
        target.setAuthVersion(version);
        if (retainCurrent && currentRequest != null) {
            HttpSession session = currentRequest.getSession(false);
            if (session != null && session.getAttribute("user") instanceof UserProfileVO current
                    && target.getUserId().equals(current.getUserId())) {
                currentRequest.changeSessionId();
                session.setAttribute(AUTH_VERSION_ATTRIBUTE, version);
            }
        }
    }

    public static long versionOf(Users user) { return user.getAuthVersion() == null ? 0L : user.getAuthVersion(); }
}
