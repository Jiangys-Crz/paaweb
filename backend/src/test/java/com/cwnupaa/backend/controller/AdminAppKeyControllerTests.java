package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.cwnupaa.backend.entity.ExternalApp;
import com.cwnupaa.backend.mapper.ExternalAppMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminAppKeyControllerTests {
    private final ExternalAppMapper mapper = mock(ExternalAppMapper.class);
    private final AdminAppKeyController controller = new AdminAppKeyController();
    private MockMvc mvc;

    @BeforeEach void setUp() {
        ReflectionTestUtils.setField(controller, "externalAppMapper", mapper);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test void generationWithoutPermissionsDefaultsToNoAccess() throws Exception {
        mvc.perform(post("/admin/app-key/generate").param("appName", "Example"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        var app = ArgumentCaptor.forClass(ExternalApp.class);
        verify(mapper).insert(app.capture());
        assertThat(app.getValue().getExternalLoginEnabled()).isFalse();
        assertThat(app.getValue().getExternalBoardEnabled()).isFalse();
    }

    @Test void generationSavesSelectedPermissions() {
        controller.generate("  Example  ", true, false);
        var app = ArgumentCaptor.forClass(ExternalApp.class);
        verify(mapper).insert(app.capture());
        assertThat(app.getValue().getAppName()).isEqualTo("Example");
        assertThat(app.getValue().getAppKey()).startsWith("ak_");
        assertThat(app.getValue().getExternalLoginEnabled()).isTrue();
        assertThat(app.getValue().getExternalBoardEnabled()).isFalse();
    }

    @Test void rejectsInvalidNameAndStatusWithoutWriting() {
        assertThat(controller.generate(" ", true, true).getCode()).isNotEqualTo(200);
        assertThat(controller.generate("a".repeat(51), true, true).getCode()).isNotEqualTo(200);
        assertThat(controller.toggleStatus(2L, 7).getCode()).isNotEqualTo(200);
        verifyNoInteractions(mapper);
    }

    @Test void permissionSaveCanRevokeBothPermissionsWithoutChangingStatus() throws Exception {
        when(mapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        mvc.perform(post("/admin/app-key/permissions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"appId\":2,\"externalLoginEnabled\":false,\"externalBoardEnabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        var update = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(mapper).update(isNull(), update.capture());
        assertThat(update.getValue().getSqlSet()).contains("external_login_enabled", "external_board_enabled").doesNotContain("status");
        assertThat(update.getValue().getParamNameValuePairs()).containsValue(false).doesNotContainValue(true);
    }

    @Test void permissionSaveRejectsMissingFlagsAndInvalidId() throws Exception {
        mvc.perform(post("/admin/app-key/permissions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"appId\":2,\"externalLoginEnabled\":true}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/admin/app-key/permissions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"appId\":-1,\"externalLoginEnabled\":true,\"externalBoardEnabled\":true}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(mapper);
    }

    @Test void missingKeyCannotBeReportedAsSuccessfulUpdateOrDeletion() throws Exception {
        assertThat(controller.toggleStatus(99L, 1).getCode()).isNotEqualTo(200);
        mvc.perform(post("/admin/app-key/permissions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"appId\":99,\"externalLoginEnabled\":true,\"externalBoardEnabled\":false}"))
                .andExpect(jsonPath("$.code").value(500));
        mvc.perform(delete("/admin/app-key/99")).andExpect(jsonPath("$.code").value(500));
    }

    @Test void deleteRemovesKeyAndStatusUpdatePreservesPermissions() throws Exception {
        when(mapper.deleteById(2L)).thenReturn(1);
        mvc.perform(delete("/admin/app-key/2")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(mapper).deleteById(2L);
        when(mapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        assertThat(controller.toggleStatus(3L, 0).getCode()).isEqualTo(200);
        var update = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(mapper).update(isNull(), update.capture());
        assertThat(update.getValue().getSqlSet()).contains("status").doesNotContain("external_login_enabled", "external_board_enabled");
    }
}
