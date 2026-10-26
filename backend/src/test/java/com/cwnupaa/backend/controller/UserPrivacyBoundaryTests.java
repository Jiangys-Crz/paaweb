package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.service.AccountSecurityService;
import com.cwnupaa.backend.utils.SecurityUtils;
import com.cwnupaa.backend.vo.PublicUserProfileVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserPrivacyBoundaryTests {
    @Test void searchSerializesOnlyPublicFieldsEvenForAFullDatabaseEntity() throws Exception {
        Users user = new Users(); user.setUserId(7L); user.setUserName("nickname"); user.setEmail("private@example.invalid"); user.setPhone("private-phone"); user.setRealName("Private Name"); user.setNumberId("private-number"); user.setBanReason("private-reason"); user.setIdPhotoFileId(8L); user.setStudentCardFileId(9L);
        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(SecurityUtils.publicSearchUser(user)));
        assertThat(json.has("userId")).isTrue(); assertThat(json.has("userName")).isTrue();
        assertThat(json.size()).isEqualTo(4);
        for (String field : java.util.List.of("email", "phone", "realName", "numberId", "role", "accountStatus", "studentCardFileId", "idPhotoFileId", "banReason", "userAccount")) assertThat(json.has(field)).as(field).isFalse();
    }
    @Test void revokedSelfSessionFallsBackToAPublicProfile() {
        var controller = new UserController(); var users = mock(UserMapper.class); var security = mock(AccountSecurityService.class);
        Users user = new Users(); user.setUserId(7L); user.setUserName("nickname"); user.setEmail("private@example.invalid");
        when(users.selectById(7L)).thenReturn(user);
        ReflectionTestUtils.setField(controller, "userMapper", users); ReflectionTestUtils.setField(controller, "accountSecurityService", security);
        ReflectionTestUtils.setField(controller, "ojUserAccountMapper", mock(OjUserAccountMapper.class)); ReflectionTestUtils.setField(controller, "ratingMapper", mock(RatingCompositionMapper.class)); ReflectionTestUtils.setField(controller, "adminPermissionsMapper", mock(AdminPermissionsMapper.class));
        var request = new MockHttpServletRequest();
        assertThat(controller.getProfile(7L, request).getData()).isInstanceOf(PublicUserProfileVO.class);
        assertThat(controller.getCurrentUser(request).getData()).isNull();
        verify(security, times(2)).validateSession(request);
    }
    @Test void ordinaryEntityUpdatesCannotResetSessionVersion() {
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "security-version-test");
        var info = com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, Users.class);
        String setClause = info.getAllSqlSet(true, "entity.");
        assertThat(setClause).contains("email").doesNotContain("auth_version").doesNotContain("authVersion");
        assertThat(info.getFieldList().stream().filter(field -> "authVersion".equals(field.getProperty())).findFirst().orElseThrow().getSqlSelect()).isEqualTo("auth_version");
    }
}
