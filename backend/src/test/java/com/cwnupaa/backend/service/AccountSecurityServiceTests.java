package com.cwnupaa.backend.service;

import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.AccountSecurityMapper;
import com.cwnupaa.backend.mapper.UserMapper;
import com.cwnupaa.backend.vo.UserProfileVO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountSecurityServiceTests {
    private final UserMapper users = mock(UserMapper.class);
    private final AccountSecurityMapper mapper = mock(AccountSecurityMapper.class);
    private final AccountSecurityService service = new AccountSecurityService(users, mapper);
    private Users user() {
        Users user = new Users(); user.setUserId(9L); user.setRole("student"); user.setAuthVersion(3L);
        when(users.selectById(9L)).thenReturn(user); return user;
    }
    private UserProfileVO profile() { var profile = new UserProfileVO(); profile.setUserId(9L); profile.setRole("student"); return profile; }

    @Test void loginRotatesAnExistingSessionAndStoresDatabaseVersion() {
        var user = user(); var request = new MockHttpServletRequest(); var oldId = request.getSession().getId();
        service.startSession(request, user, profile());
        assertThat(request.getSession().getId()).isNotEqualTo(oldId);
        assertThat(service.validateSession(request)).isSameAs(user);
    }
    @Test void revokedSessionCannotUsePrivateProfileAttributes() {
        var user = user(); var request = new MockHttpServletRequest();
        service.startSession(request, user, profile()); request.setAttribute("user", profile()); request.setAttribute("userId", 9L);
        user.setAuthVersion(4L);
        assertThat(service.validateSession(request)).isNull();
        assertThat(request.getSession(false)).isNull(); assertThat(request.getAttribute("user")).isNull();
    }
    @Test void preMigrationSessionWithoutVersionMustLoginAgain() {
        user(); var request = new MockHttpServletRequest(); request.getSession().setAttribute("user", profile());
        assertThat(service.validateSession(request)).isNull(); assertThat(request.getSession(false)).isNull();
    }
    @Test void securityChangeKeepsOnlyCurrentSessionAndRotatesIt() {
        var user = user(); var current = new MockHttpServletRequest(); var other = new MockHttpServletRequest();
        service.startSession(current, user, profile()); service.startSession(other, user, profile());
        String before = current.getSession().getId(); when(mapper.incrementAuthVersion(9L)).thenReturn(4L);
        service.revokeSessions(user, current, true);
        assertThat(current.getSession().getId()).isNotEqualTo(before);
        assertThat(service.validateSession(current)).isSameAs(user);
        assertThat(service.validateSession(other)).isNull();
    }
    @Test void retiredTeacherRoleCannotKeepAValidVersionSession() {
        var user = user(); var request = new MockHttpServletRequest(); service.startSession(request, user, profile());
        user.setRole("teacher"); assertThat(service.validateSession(request)).isNull();
    }
}
