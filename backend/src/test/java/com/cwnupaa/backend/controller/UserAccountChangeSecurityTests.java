package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.dto.UserUpdateDTO;
import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.service.*;
import com.cwnupaa.backend.utils.Md5Util;
import com.cwnupaa.backend.vo.UserProfileVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserAccountChangeSecurityTests {
    private final UserController controller = new UserController();
    private final UserMapper users = mock(UserMapper.class);
    private final AccountSecurityService security = mock(AccountSecurityService.class);
    private final SecurityAuditService audit = mock(SecurityAuditService.class);
    private final EmailService email = mock(EmailService.class);
    private final Users user = new Users();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private static final String CURRENT = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    @BeforeEach void setup() {
        for (String name : List.of("codeCache", "codeTimeCache", "codeSendTimeCache", "codeFailCache", "codeLockCache")) ((Map<?, ?>) ReflectionTestUtils.getField(UserController.class, name)).clear();
        user.setUserId(8L); user.setRole("student"); user.setAccountStatus("normal"); user.setEmail("old@example.invalid"); user.setPassword(Md5Util.encrypt(CURRENT));
        when(users.selectById(8L)).thenReturn(user);
        when(users.selectOne(any(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class))).thenReturn(user);
        when(users.updateIdPhoto(eq(8L), anyLong(), any())).thenAnswer(call -> {
            user.setIdPhotoFileId(call.getArgument(1));
            user.setIdPhotoUpdatedAt(call.getArgument(2));
            return 1;
        });
        when(security.validateSession(request)).thenReturn(user);
        var snapshot = new UserProfileVO(); snapshot.setUserId(8L); snapshot.setRole("student"); snapshot.setAccountStatus("normal");
        request.getSession().setAttribute("user", snapshot); request.setAttribute("user", snapshot);
        ReflectionTestUtils.setField(controller, "userMapper", users);
        ReflectionTestUtils.setField(controller, "accountSecurityService", security);
        ReflectionTestUtils.setField(controller, "securityAuditService", audit);
        ReflectionTestUtils.setField(controller, "emailService", email);
        ReflectionTestUtils.setField(controller, "jdbcTemplate", mock(JdbcTemplate.class));
        ReflectionTestUtils.setField(controller, "ojUserAccountMapper", mock(OjUserAccountMapper.class));
        ReflectionTestUtils.setField(controller, "ratingMapper", mock(RatingCompositionMapper.class));
        ReflectionTestUtils.setField(controller, "adminPermissionsMapper", mock(AdminPermissionsMapper.class));
    }
    private void code(String email, String purpose) { ReflectionTestUtils.invokeMethod(controller, "cacheEmailCode", email, "123456", purpose, 8L); }
    @Test void newMailboxProofAloneCannotTakeOverAnAccount() {
        code("new@example.invalid", "change-email-new");
        var dto = new UserUpdateDTO(); dto.setEmail("new@example.invalid"); dto.setNewEmailVerifyCode("123456"); dto.setPassword("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        assertThat(controller.update(dto, request).getCode()).isNotEqualTo(200);
        verify(users, never()).updateById(any(Users.class)); verify(security, never()).revokeSessions(any(), any(), anyBoolean()); verifyNoInteractions(audit);
    }
    @Test void emailVerificationDoesNotSubstituteForPasswordVerification() {
        code("old@example.invalid", "change-email-current"); code("new@example.invalid", "change-email-new");
        var dto = new UserUpdateDTO(); dto.setEmail("new@example.invalid"); dto.setOldVerifyCode("123456"); dto.setNewEmailVerifyCode("123456"); dto.setPassword("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        assertThat(controller.update(dto, request).getCode()).isNotEqualTo(200);
        verify(users, never()).updateById(any(Users.class)); assertThat(user.getPassword()).isEqualTo(Md5Util.encrypt(CURRENT));
    }
    @Test void loginCodeCannotBeUsedAsAPasswordChangeCode() {
        ReflectionTestUtils.invokeMethod(controller, "cacheEmailCode", "old@example.invalid", "123456", "login", null);
        var dto = new UserUpdateDTO(); dto.setPassword("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"); dto.setVerifyCode("123456");
        assertThat(controller.update(dto, request).getCode()).isNotEqualTo(200); verify(users, never()).updateById(any(Users.class));
    }
    @Test void verifiedEmailChangeRevokesOtherSessionsAndNotifiesOriginalAddress() throws Exception {
        code("new@example.invalid", "change-email-new");
        var dto = new UserUpdateDTO(); dto.setEmail("new@example.invalid"); dto.setOldPassword(CURRENT); dto.setNewEmailVerifyCode("123456");
        assertThat(controller.update(dto, request).getCode()).isEqualTo(200);
        verify(security).revokeSessions(user, request, true); verify(email).sendAccountSecurityChangedEmail(eq("old@example.invalid"), eq(List.of("email")));
        verify(audit).record(8L, 8L, "self-account-security-change", List.of("email"));
    }
    @Test void passwordChangeRequiresItsOwnPurposeAndDoesNotNeedEmailChange() throws Exception {
        code("old@example.invalid", "change-password");
        var dto = new UserUpdateDTO(); dto.setPassword("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"); dto.setVerifyCode("123456");
        assertThat(controller.update(dto, request).getCode()).isEqualTo(200);
        verify(security).revokeSessions(user, request, true); assertThat(user.getPassword()).isEqualTo(Md5Util.encrypt(dto.getPassword()));
    }
    @Test void requestsRevokedWhileWaitingForTheUserLockCannotOverwriteSensitiveState() {
        when(users.selectOne(any(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class))).thenAnswer(call -> {
            var query = (com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<?>) call.getArgument(0);
            assertThat(query.getSqlSegment()).endsWith("FOR UPDATE");
            return user;
        });
        when(security.validateSession(request)).thenReturn(null);
        assertThat(controller.updateBg(Map.of("backgroundFileId", 9L), request).getCode()).isEqualTo(401);
        assertThat(controller.updateIdPhoto(9L, request).getCode()).isEqualTo(401);
        assertThat(controller.submitAuth(9L, request).getCode()).isEqualTo(401);
        assertThat(controller.revokeAuth(request).getCode()).isEqualTo(401);
        assertThat(controller.activate(new com.cwnupaa.backend.dto.UserActivateDTO(), request).getCode()).isEqualTo(401);
        verify(users, never()).updateById(any(Users.class));
        verifyNoInteractions(audit);
    }
    @Test void authenticatedUserCanUploadThenReplaceOwnPhotoWithoutChangingSignupOrIdentity() {
        var files = mock(FileAccessPolicy.class);
        ReflectionTestUtils.setField(controller, "fileAccessPolicy", files);
        ReflectionTestUtils.setField(controller, "fileSystemMapper", mock(FileSystemMapper.class));

        assertThat(controller.updateIdPhoto(9L, request).getCode()).isEqualTo(200);
        assertThat(user.getIdPhotoFileId()).isEqualTo(9L);
        assertThat(user.getIdPhotoUpdatedAt()).isNotNull();
        assertThat(controller.updateIdPhoto(11L, request).getCode()).isEqualTo(200);
        assertThat(user.getIdPhotoFileId()).isEqualTo(11L);
        var snapshot = (UserProfileVO) request.getSession().getAttribute("user");
        assertThat(snapshot.getIdPhotoFileId()).isEqualTo(11L);
        assertThat(snapshot.getIdPhotoUrl()).isEqualTo("/api/user/id-photo/8");
        assertThat(user.getAccountStatus()).isEqualTo("normal");
        verify(files).validatePrivateFile(eq(9L), any(UserProfileVO.class), eq(true));
        verify(files).validatePrivateFile(eq(11L), any(UserProfileVO.class), eq(true));
        verify(files).deleteIfUnreferencedAfterCommit(eq(9L), any(UserProfileVO.class));
        verify(users, times(2)).updateIdPhoto(eq(8L), anyLong(), any());
        verify(users, never()).updateById(any(Users.class));
    }
}
