package com.cwnupaa.backend.config;

import com.cwnupaa.backend.entity.AdminPermissions;
import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.AdminPermissionsMapper;
import com.cwnupaa.backend.service.AccountSecurityService;
import com.cwnupaa.backend.service.AuthStateChangeService;
import com.cwnupaa.backend.vo.UserProfileVO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ResolverParticipantAuthorizationTests {
    private static final String ROUTE = "/leaderboard/admin/7/resolver/participants";

    @Test void finalResultsRequireContestManagementPermission() throws Exception {
        var interceptor = new LoginInterceptor();
        var accountSecurity = mock(AccountSecurityService.class);
        var permissions = mock(AdminPermissionsMapper.class);
        ReflectionTestUtils.setField(interceptor, "accountSecurityService", accountSecurity);
        ReflectionTestUtils.setField(interceptor, "adminPermissionsMapper", permissions);
        ReflectionTestUtils.setField(interceptor, "authStateChangeService", mock(AuthStateChangeService.class));

        var anonymous = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(new MockHttpServletRequest("GET", ROUTE), anonymous, new Object())).isFalse();
        assertThat(anonymous.getStatus()).isEqualTo(401);
        assertThat(anonymous.getContentAsString()).doesNotContain("participants", "solved", "penalty");

        var dbUser = new Users(); dbUser.setUserId(2L); dbUser.setRole("student"); dbUser.setAccountStatus("normal");
        when(accountSecurity.validateSession(any())).thenReturn(dbUser);
        var sessionUser = new UserProfileVO(); sessionUser.setUserId(2L); sessionUser.setRole("student"); sessionUser.setAccountStatus("normal");
        var request = new MockHttpServletRequest("GET", ROUTE); request.getSession().setAttribute("user", sessionUser);
        var student = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request, student, new Object())).isFalse();
        assertThat(student.getStatus()).isEqualTo(403);
        assertThat(student.getContentAsString()).doesNotContain("participants", "solved", "penalty");

        dbUser.setRole("stu_admin");
        var grant = new AdminPermissions(); grant.setPermissions("[\"manage_notice\"]");
        when(permissions.selectOne(any())).thenReturn(grant);
        var withoutContest = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request, withoutContest, new Object())).isFalse();
        assertThat(withoutContest.getStatus()).isEqualTo(403);
        assertThat(withoutContest.getContentAsString()).doesNotContain("participants", "solved", "penalty");
        grant.setPermissions("[\"manage_contest\"]");
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }
}
