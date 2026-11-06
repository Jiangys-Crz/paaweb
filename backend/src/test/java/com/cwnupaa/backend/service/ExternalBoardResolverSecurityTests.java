package com.cwnupaa.backend.service;

import com.cwnupaa.backend.dto.ExternalBoardConfigDTO;
import com.cwnupaa.backend.dto.ResolverSettingsDTO;
import com.cwnupaa.backend.entity.SignupTeam;
import com.cwnupaa.backend.mapper.SignupTeamMapper;
import com.cwnupaa.backend.entity.Event;
import com.cwnupaa.backend.entity.ExternalBoard;
import com.cwnupaa.backend.mapper.EventMapper;
import com.cwnupaa.backend.mapper.ExternalBoardMapper;
import com.cwnupaa.backend.mapper.SubmitRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExternalBoardResolverSecurityTests {
    private ExternalBoardService service;
    private Event event;
    private ExternalBoard board;
    private SubmitRecordMapper submissions;
    private ExternalBoardMapper boards;

    @BeforeEach void setup() {
        service = new ExternalBoardService();
        var events = mock(EventMapper.class);
        boards = mock(ExternalBoardMapper.class);
        submissions = mock(SubmitRecordMapper.class);
        ReflectionTestUtils.setField(service, "eventMapper", events);
        ReflectionTestUtils.setField(service, "externalBoardMapper", boards);
        ReflectionTestUtils.setField(service, "submitRecordMapper", submissions);
        event = new Event(); event.setEventId(4L); event.setType("XCPC");
        event.setEventStart(LocalDateTime.now().minusHours(6)); event.setEventEnd(LocalDateTime.now().minusHours(1));
        board = new ExternalBoard(); board.setBoardId(5L); board.setEventId(4L); board.setExternalOpen(true);
        board.setResolverEnabled(true); board.setUnfrozen(false);
        board.setResolverPasswordHash(new BCryptPasswordEncoder(4).encode("test-password-123"));
        when(events.selectById(4L)).thenReturn(event); when(boards.selectById(5L)).thenReturn(board);
        when(boards.lockById(5L)).thenReturn(board);
    }

    @Test void rejectsMissingOrWrongPasswordWithoutReadingAnyResults() {
        assertThatThrownBy(() -> service.resolveBoard(5L, null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.resolveBoard(5L, "wrong-password")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(submissions);
    }

    @Test void refusesDisabledClosedNonXcpcAndRunningBoardsWithoutReadingResults() {
        board.setResolverEnabled(false);
        assertThatThrownBy(() -> service.resolveBoard(5L, "test-password-123")).isInstanceOf(ResponseStatusException.class);
        board.setResolverEnabled(true); board.setExternalOpen(false);
        assertThatThrownBy(() -> service.resolveBoard(5L, "test-password-123")).isInstanceOf(ResponseStatusException.class);
        board.setExternalOpen(true); event.setType("GPLT");
        assertThatThrownBy(() -> service.resolveBoard(5L, "test-password-123")).isInstanceOf(ResponseStatusException.class);
        event.setType("XCPC"); event.setEventEnd(LocalDateTime.now().plusHours(1));
        assertThatThrownBy(() -> service.resolveBoard(5L, "test-password-123")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(submissions);
    }

    @Test void configuringUnfrozenBoardDoesNotRequirePasswordButRefreezingDoes() {
        board.setResolverPasswordHash(null);
        var dto = new ExternalBoardConfigDTO();
        dto.setBoardId(5L); dto.setName("Test board"); dto.setExternalOpen(true);
        dto.setUnfrozen(true); dto.setResolverEnabled(true);
        service.updateConfig(dto);
        verify(boards).updateById(board);
        reset(boards);
        when(boards.selectById(5L)).thenReturn(board); when(boards.lockById(5L)).thenReturn(board);
        dto.setUnfrozen(false);
        assertThatThrownBy(() -> service.updateConfig(dto)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("封榜时启用滚榜必须设置密码");
        verify(boards, never()).updateById(board);
        dto.setUnfrozen(true); event.setType("GPLT");
        assertThatThrownBy(() -> service.updateConfig(dto)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("仅 XCPC 外榜可启用滚榜");
        verify(boards, never()).updateById(board);
    }

    @Test void publicSubmissionPaginationCannotDisableLimits() {
        for (int size : new int[]{-1,0,201,Integer.MAX_VALUE})
            assertThatThrownBy(() -> service.getBoardSubmissions(5L,1,size,null,null,null))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        verifyNoInteractions(submissions);
    }

    @Test void playbackPreferencesAreValidatedAndPersistedPerBoard() {
        var dto = new ExternalBoardConfigDTO();
        dto.setBoardId(5L); dto.setName("Test board"); dto.setExternalOpen(true); dto.setUnfrozen(true);
        dto.setResolverSingleStep(false); dto.setResolverSpeed(0.25);
        service.updateConfig(dto);
        assertThat(board.getResolverSingleStep()).isFalse();
        assertThat(board.getResolverSpeed()).isEqualTo(0.25);
        dto.setResolverSpeed(4.0); service.updateConfig(dto);
        assertThat(board.getResolverSpeed()).isEqualTo(4.0);
        for (double invalid : new double[]{0.24, 4.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            dto.setResolverSpeed(invalid);
            assertThatThrownBy(() -> service.updateConfig(dto)).isInstanceOf(ResponseStatusException.class);
        }
    }

    @Test void settingsRejectInvalidDisplayAndTeamsOutsideEvent() {
        var dto = new ExternalBoardConfigDTO(); dto.setBoardId(5L); dto.setName("Test board"); dto.setExternalOpen(true); dto.setUnfrozen(true);
        var settings = new ResolverSettingsDTO(); dto.setResolverSettings(settings);
        settings.getDisplay().setRowsPerScreen(31);
        assertThatThrownBy(() -> service.updateConfig(dto)).isInstanceOf(ResponseStatusException.class);
        settings.getDisplay().setRowsPerScreen(12);
        var award = new ResolverSettingsDTO.CustomAward(); award.setCitation("Prize"); award.setParticipantIds(java.util.List.of(8L));
        settings.getAwards().setCustomAwards(java.util.List.of(award)); event.setIsTeam(1);
        var teams = mock(SignupTeamMapper.class); ReflectionTestUtils.setField(service, "signupTeamMapper", teams);
        when(teams.selectList(any())).thenReturn(java.util.List.of());
        assertThatThrownBy(() -> service.updateConfig(dto)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("当前赛事");
        var valid = new SignupTeam(); valid.setSignupId(8L); when(teams.selectList(any())).thenReturn(java.util.List.of(valid));
        service.updateConfig(dto);
        assertThat(board.getResolverSettings()).contains("Prize");
    }

    @Test void limitsRepeatedPasswordAttemptsBeforeReadingResults() {
        for (int i = 0; i < 10; i++) assertThatThrownBy(() -> service.resolveBoard(5L, "wrong")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.resolveBoard(5L, "test-password-123"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("429");
        verifyNoInteractions(submissions);
    }
}
