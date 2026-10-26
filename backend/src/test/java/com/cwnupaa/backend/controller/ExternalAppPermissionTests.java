package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.dto.ExternalLoginDTO;
import com.cwnupaa.backend.dto.ExternalSendCodeDTO;
import com.cwnupaa.backend.entity.ExternalApp;
import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.ExternalAppMapper;
import com.cwnupaa.backend.mapper.UserMapper;
import com.cwnupaa.backend.service.EmailService;
import com.cwnupaa.backend.service.ExternalBoardService;
import com.cwnupaa.backend.utils.Md5Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ExternalAppPermissionTests {
    private final ExternalAppMapper appMapper = mock(ExternalAppMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final EmailService emailService = mock(EmailService.class);
    private final ExternalBoardService boardService = mock(ExternalBoardService.class);
    private final UserController users = new UserController();
    private final ExternalSubmitController board = new ExternalSubmitController();
    private ExternalApp app;

    @AfterEach
    void clearTestEmailCode() {
        for (String field : List.of("codeCache", "codeTimeCache", "codeSendTimeCache", "codeFailCache", "codeLockCache")) {
            ((Map<?, ?>) ReflectionTestUtils.getField(UserController.class, field)).keySet().removeIf(key -> key.toString().endsWith("api-permissions@example.com"));
        }
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(users, "externalAppMapper", appMapper);
        ReflectionTestUtils.setField(users, "userMapper", userMapper);
        ReflectionTestUtils.setField(users, "emailService", emailService);
        ReflectionTestUtils.setField(board, "externalAppMapper", appMapper);
        ReflectionTestUtils.setField(board, "externalBoardService", boardService);
        app = new ExternalApp();
        app.setAppKey("ak_test");
        app.setStatus(1);
        // Emulate the mapper filtering by key and enabled status, including after deletion.
        when(appMapper.selectOne(any(QueryWrapper.class))).thenAnswer(invocation -> {
            QueryWrapper<ExternalApp> query = invocation.getArgument(0);
            query.getSqlSegment();
            Map<String, Object> params = query.getParamNameValuePairs();
            return app != null && Integer.valueOf(1).equals(app.getStatus())
                    && params.containsValue(app.getAppKey()) && params.containsValue(1) ? app : null;
        });
    }

    private ExternalLoginDTO login(String type) {
        ExternalLoginDTO dto = new ExternalLoginDTO();
        dto.setAppKey("ak_test"); dto.setAccount("test_user");
        dto.setCredential("test_password"); dto.setLoginType(type);
        return dto;
    }

    private ExternalSendCodeDTO sendCode() {
        ExternalSendCodeDTO dto = new ExternalSendCodeDTO();
        dto.setAppKey("ak_test"); dto.setEmail("api-permissions@example.com");
        return dto;
    }

    private void assertAllDenied() {
        List<Result<?>> results = List.of(users.externalLogin(login("password")), users.externalLogin(login("code")),
                users.externalSendCode(sendCode()),
                board.receiveSubmissionsBatch(1L, Map.of("api_key", "ak_test", "submissions", List.of())),
                board.receiveProblemsBatch(1L, Map.of("api_key", "ak_test", "problems", List.of())));
        for (Result<?> result : results) assertThat(result.getCode()).isNotEqualTo(200);
        verifyNoInteractions(userMapper, emailService, boardService);
    }

    @Test void unassignedOrNullPermissionsNeverAuthorizeAnyEndpoint() {
        assertAllDenied();
        app.setExternalLoginEnabled(false); app.setExternalBoardEnabled(false);
        assertAllDenied();
    }

    @Test void disabledAndDeletedKeysCannotUsePreviouslyGrantedPermissions() {
        app.setExternalLoginEnabled(true); app.setExternalBoardEnabled(true);
        app.setStatus(0);
        assertAllDenied();
        app = null;
        assertAllDenied();
    }

    @Test void loginOnlyKeyCanLogInButCannotUploadBoardData() {
        app.setExternalLoginEnabled(true); app.setExternalBoardEnabled(false);
        Users user = new Users(); user.setUserId(3L); user.setUserAccount("test_user");
        user.setRole("student"); user.setAccountStatus("normal");
        user.setPassword(Md5Util.encrypt(Md5Util.md5("test_password")));
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(user);
        assertThat(users.externalLogin(login("password")).getCode()).isEqualTo(200);
        assertThat(board.receiveSubmissionsBatch(1L, Map.of("api_key", "ak_test")).getCode()).isEqualTo(403);
        assertThat(board.receiveProblemsBatch(1L, Map.of("api_key", "ak_test")).getCode()).isEqualTo(403);
        verifyNoInteractions(boardService);
    }

    @Test void boardOnlyKeyCanUploadBothResourcesButCannotLogInOrSendCodes() {
        app.setExternalLoginEnabled(false); app.setExternalBoardEnabled(true);
        var rows = List.<Map<String, Object>>of(Map.of("id", "example"));
        when(boardService.processExternalSubmissionsBatch(1L, rows)).thenReturn(1);
        when(boardService.processExternalProblemsBatch(1L, rows)).thenReturn(1);
        assertThat(board.receiveSubmissionsBatch(1L, Map.of("api_key", "ak_test", "submissions", rows)).getCode()).isEqualTo(200);
        assertThat(board.receiveProblemsBatch(1L, Map.of("api_key", "ak_test", "problems", rows)).getCode()).isEqualTo(200);
        assertThat(users.externalLogin(login("password")).getCode()).isEqualTo(403);
        assertThat(users.externalSendCode(sendCode()).getCode()).isEqualTo(403);
        verify(boardService).processExternalSubmissionsBatch(1L, rows);
        verify(boardService).processExternalProblemsBatch(1L, rows);
        verifyNoInteractions(userMapper, emailService);
    }

    @Test void revokingPermissionsTakesEffectOnNextRequest() {
        app.setExternalBoardEnabled(true);
        assertThat(board.receiveProblemsBatch(1L, Map.of("api_key", "ak_test", "problems", List.of())).getCode()).isEqualTo(200);
        app.setExternalBoardEnabled(false);
        assertThat(board.receiveProblemsBatch(1L, Map.of("api_key", "ak_test", "problems", List.of())).getCode()).isEqualTo(403);
    }

    @Test void grantingBothPermissionsAllowsLoginCodeAndBoardUpload() throws Exception {
        app.setExternalLoginEnabled(true); app.setExternalBoardEnabled(true);
        when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        assertThat(users.externalSendCode(sendCode()).getCode()).isEqualTo(200);
        verify(emailService).sendCodeEmail(eq("api-permissions@example.com"), anyString(), eq("login"));
        assertThat(board.receiveProblemsBatch(1L, Map.of("api_key", "ak_test", "problems", List.of())).getCode()).isEqualTo(200);
    }

    @Test void invalidKeyAndMissingKeyDoNotReachBusinessLogic() {
        app.setExternalLoginEnabled(true); app.setExternalBoardEnabled(true);
        ExternalLoginDTO dto = login("password"); dto.setAppKey("wrong_key");
        assertThat(users.externalLogin(dto).getCode()).isNotEqualTo(200);
        assertThat(board.receiveSubmissionsBatch(1L, Map.of()).getCode()).isNotEqualTo(200);
        assertThat(board.receiveProblemsBatch(1L, Map.of("api_key", "wrong_key")).getCode()).isNotEqualTo(200);
        verifyNoInteractions(userMapper, emailService, boardService);
    }
}
