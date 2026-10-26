package com.cwnupaa.backend.config;

import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.UserMapper;
import com.cwnupaa.backend.service.AuthStateChangeService;
import com.cwnupaa.backend.vo.UserProfileVO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LoginInterceptorBanTests {
    @Test void existingSessionCannotModifyDataAfterBan() throws Exception {
        LoginInterceptor interceptor = new LoginInterceptor();
        UserMapper mapper = mock(UserMapper.class);
        ReflectionTestUtils.setField(interceptor, "userMapper", mapper);
        var accountSecurity = mock(com.cwnupaa.backend.service.AccountSecurityService.class);
        ReflectionTestUtils.setField(interceptor, "accountSecurityService", accountSecurity);
        ReflectionTestUtils.setField(interceptor, "authStateChangeService", mock(AuthStateChangeService.class));
        UserProfileVO sessionUser = new UserProfileVO();
        sessionUser.setUserId(2L);
        sessionUser.setRole("student");
        sessionUser.setAccountStatus("normal");
        Users dbUser = new Users();
        dbUser.setUserId(2L);
        dbUser.setRole("student");
        dbUser.setAccountStatus("banned");
        when(mapper.selectById(2L)).thenReturn(dbUser);
        when(accountSecurity.validateSession(any())).thenReturn(dbUser);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/user/update");
        request.getSession().setAttribute("user", sessionUser);
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("账号已被封禁");
    }
}
