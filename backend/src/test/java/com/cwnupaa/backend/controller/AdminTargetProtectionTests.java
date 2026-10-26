package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.UserMapper;
import com.cwnupaa.backend.service.AccountSecurityService;
import com.cwnupaa.backend.service.SecurityAuditService;
import com.cwnupaa.backend.vo.UserProfileVO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminTargetProtectionTests {
    private final UserMapper users = mock(UserMapper.class);
    private final AccountSecurityService sessions = mock(AccountSecurityService.class);
    private final SecurityAuditService audit = mock(SecurityAuditService.class);
    private final AdminUserController controller = new AdminUserController();
    AdminTargetProtectionTests() {
        ReflectionTestUtils.setField(controller, "userMapper", users);
        ReflectionTestUtils.setField(controller, "accountSecurityService", sessions);
        ReflectionTestUtils.setField(controller, "securityAuditService", audit);
    }
    private Users target(String role, String account) {
        var target = new Users(); target.setUserId(1L); target.setRole(role); target.setUserAccount(account); target.setEmail("old@example.invalid");
        when(users.selectById(1L)).thenReturn(target); when(users.selectOne(any(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class))).thenReturn(target); return target;
    }
    private MockHttpServletRequest operator(String role, List<String> permissions) {
        var actor = new UserProfileVO(); actor.setUserId(2L); actor.setUserAccount("operator"); actor.setRole(role); actor.setAccountStatus("normal"); actor.setPermissions(permissions);
        var request = new MockHttpServletRequest(); request.setAttribute("user", actor); return request;
    }
    @Test void manageUserCannotReplaceRootMailboxEvenWithoutRoleChange() {
        var root = target("supper_admin", "admin"); var update = new Users(); update.setUserId(1L); update.setEmail("new@example.invalid");
        assertThat(controller.updateUserInfo(update, operator("stu_admin", List.of("manage_user", "manage_user_email"))).getCode()).isEqualTo(403);
        assertThat(root.getEmail()).isEqualTo("old@example.invalid"); verify(users, never()).updateById(any(Users.class)); verifyNoInteractions(sessions, audit);
    }
    @Test void peersCannotModifyEachOtherEvenWithSensitivePermissions() {
        target("stu_admin", "peer"); var update = new Users(); update.setUserId(1L); update.setAccountStatus("normal");
        assertThat(controller.updateUserInfo(update, operator("stu_admin", List.of("manage_user", "manage_user_status"))).getCode()).isEqualTo(403);
        verify(users, never()).updateById(any(Users.class));
    }
    @Test void genericUserPermissionDoesNotAuthorizeMailboxOrStatusChange() {
        target("student", "student"); var update = new Users(); update.setUserId(1L); update.setEmail("new@example.invalid");
        assertThat(controller.updateUserInfo(update, operator("stu_admin", List.of("manage_user"))).getCode()).isEqualTo(403);
        update.setEmail(null); update.setAccountStatus("normal");
        assertThat(controller.updateUserInfo(update, operator("stu_admin", List.of("manage_user"))).getCode()).isEqualTo(403);
        verify(users, never()).updateById(any(Users.class));
    }
    @Test void identityAuditCannotBeUsedAgainstRootOrHigherLevelAccounts() {
        var root = target("supper_admin", "admin"); root.setAccountStatus("pending");
        assertThat(controller.auditAuth(java.util.Map.of("userId", 1L, "pass", true, "reason", "checked"), operator("stu_admin", List.of("manage_user", "manage_user_status"))).getCode()).isEqualTo(403);
        verify(users, never()).updateById(any(Users.class));
    }
    @Test void authorizationUsesTheLockedCurrentRoleRatherThanAStaleUnprivilegedSnapshot() {
        Users stale = target("student", "user");
        Users promoted = new Users(); promoted.setUserId(1L); promoted.setUserAccount("user"); promoted.setRole("supper_admin");
        when(users.selectOne(any(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class))).thenAnswer(call -> {
            var query = (com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<?>) call.getArgument(0);
            assertThat(query.getSqlSegment()).endsWith("FOR UPDATE");
            return promoted;
        });
        var update = new Users(); update.setUserId(1L); update.setEmail("new@example.invalid");
        assertThat(controller.updateUserInfo(update, operator("stu_admin", List.of("manage_user", "manage_user_email"))).getCode()).isEqualTo(403);
        assertThat(stale.getEmail()).isEqualTo("old@example.invalid"); verify(users, never()).updateById(any(Users.class));
    }
}
