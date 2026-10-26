package com.cwnupaa.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.UserMapper;
import com.cwnupaa.backend.vo.UserProfileVO;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserBanServiceTests {
    private final UserMapper mapper = mock(UserMapper.class);
    private final AuthStateChangeService auth = mock(AuthStateChangeService.class);
    private final UserBanService service = new UserBanService(mapper, auth, mock(AccountSecurityService.class), mock(SecurityAuditService.class));
    private UserProfileVO operator() {
        UserProfileVO operator = new UserProfileVO();
        operator.setUserId(1L);
        operator.setRole("supper_admin");
        operator.setUserAccount("admin");
        operator.setAccountStatus("normal");
        return operator;
    }
    private Users target() {
        Users user = new Users();
        user.setUserId(2L);
        user.setRole("student");
        user.setEmail("student@example.com");
        user.setAccountStatus("pending");
        when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(user);
        return user;
    }
    @Test void banPreservesPreviousStatusAndNotifiesOnlineUser() {
        Users user = target();
        var until = UserBanService.now().plusDays(1);
        service.ban(2L, until, "  违规行为  ", operator());
        assertThat(user.getAccountStatus()).isEqualTo("banned");
        assertThat(user.getPreBanStatus()).isEqualTo("pending");
        assertThat(user.getBanUntil()).isEqualTo(until);
        assertThat(user.getBanReason()).isEqualTo("违规行为");
        verify(mapper).updateById(user);
        verify(auth).notifyChanged(2L);
    }
    @Test void rejectsPastDateAndBlankReason() {
        assertThatThrownBy(() -> service.ban(2L, UserBanService.now().minusMinutes(1), "原因", operator())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.ban(2L, UserBanService.now().plusDays(1), "  ", operator())).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mapper);
    }
    @Test void rejectsMissingEmailAlreadyBannedAndProtectedAccounts() {
        Users user = target();
        user.setEmail(null);
        assertThatThrownBy(() -> service.ban(2L, UserBanService.now().plusDays(1), "原因", operator())).hasMessageContaining("邮箱");
        user.setEmail("student@example.com");
        user.setAccountStatus("banned");
        assertThatThrownBy(() -> service.ban(2L, UserBanService.now().plusDays(1), "原因", operator())).hasMessageContaining("已被封禁");
        user.setRole("supper_admin");
        assertThatThrownBy(() -> service.ban(2L, UserBanService.now().plusDays(1), "原因", operator())).hasMessageContaining("超级管理员");
        verify(mapper, never()).updateById(any(Users.class));
    }
    @Test void expiryNotifiesOnlyWhenConditionalUpdateSucceeds() {
        Users user = target();
        user.setBanUntil(UserBanService.now().minusMinutes(1));
        user.setPreBanStatus("pending");
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(user));
        when(mapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1, 0);
        service.releaseExpiredBans();
        service.releaseExpiredBans();
        verify(auth, times(1)).notifyChanged(2L);
    }
}
