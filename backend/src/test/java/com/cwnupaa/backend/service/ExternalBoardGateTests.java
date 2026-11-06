package com.cwnupaa.backend.service;

import com.cwnupaa.backend.entity.Event;
import com.cwnupaa.backend.entity.SubmitRecord;
import com.cwnupaa.backend.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExternalBoardGateTests {
    private ExternalBoardService service;
    private EventMapper events;
    private SubmitRecordMapper submissions;
    private EventProblemMapper problems;
    @BeforeEach void setup() {
        service = new ExternalBoardService(); events = mock(EventMapper.class);
        submissions = mock(SubmitRecordMapper.class); problems = mock(EventProblemMapper.class);
        ReflectionTestUtils.setField(service,"eventMapper",events);
        com.cwnupaa.backend.support.ExternalBoardFixtures.attach(service, events);
        ReflectionTestUtils.setField(service,"submitRecordMapper",submissions);
        ReflectionTestUtils.setField(service,"eventProblemMapper",problems);
    }
    @Test void allPublicReadsDenyClosedAndUnsetBoardsBeforeQueryingDetails() {
        for (Boolean open : new Boolean[]{false,null}) {
            Event event = new Event(); event.setEventId(1L); event.setExternalOpen(open);
            when(events.selectById(1L)).thenReturn(event);
            assertThatThrownBy(() -> service.getBoardDetail(1L,null,"team",null,null)).isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> service.getStatistics(1L,null)).isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> service.getBoardSubmissions(1L,1,20,null,null,null)).isInstanceOf(ResponseStatusException.class);
            verifyNoInteractions(submissions,problems);
        }
    }
    @Test void closedBoardCannotLeakCodeAfterContestEndsAndUnfreezes() {
        Event event = new Event(); event.setExternalOpen(false); event.setEventEnd(LocalDateTime.now().minusDays(1)); event.setUnfrozen(true);
        SubmitRecord record = new SubmitRecord(); record.setEventId(1L); record.setBoardId(1L); record.setCodeContent("private");
        when(submissions.selectById(2L)).thenReturn(record); when(events.selectById(1L)).thenReturn(event);
        assertThatThrownBy(() -> service.getSubmissionCode(2L)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void codeStillRequiresContestEndAndUnfreezeForOpenBoards() {
        Event event = new Event(); event.setExternalOpen(true); event.setEventEnd(LocalDateTime.now().plusDays(1)); event.setUnfrozen(true);
        event.setEventStart(LocalDateTime.now().minusDays(3));
        SubmitRecord record = new SubmitRecord(); record.setEventId(1L); record.setBoardId(1L); record.setCodeContent("public"); record.setSubmitTime(LocalDateTime.now().minusDays(2));
        when(submissions.selectById(2L)).thenReturn(record); when(events.selectById(1L)).thenReturn(event);
        assertThatThrownBy(() -> service.getSubmissionCode(2L)).hasMessageContaining("running");
        event.setEventEnd(LocalDateTime.now().minusDays(1)); event.setUnfrozen(false);
        assertThatThrownBy(() -> service.getSubmissionCode(2L)).hasMessageContaining("unfrozen");
        event.setUnfrozen(true); assertThat(service.getSubmissionCode(2L).get("content")).isEqualTo("public");
    }
    @Test void endedBoardExposesExplicitUnfreezeState() {
        service = spy(service);
        Event event = new Event();
        event.setEventId(1L); event.setExternalOpen(true); event.setIsTeam(1);
        event.setEventStart(LocalDateTime.now().minusDays(2));
        event.setEventEnd(LocalDateTime.now().minusDays(1));
        when(events.selectById(1L)).thenReturn(event);
        var teams = mock(SignupTeamMapper.class);
        ReflectionTestUtils.setField(service, "signupTeamMapper", teams);
        var config = new com.cwnupaa.backend.dto.ExternalBoardConfigDTO();
        config.setProblems(java.util.List.of());
        config.setMedalConfig(new com.cwnupaa.backend.dto.ExternalBoardConfigDTO.MedalConfig());
        doReturn(config).when(service).getConfig(1L);
        for (Boolean unfrozen : new Boolean[]{null, false, true}) {
            event.setUnfrozen(unfrozen);
            var board = service.getBoardDetail(1L, null, "team", null, null);
            assertThat(board.getStatus()).isEqualTo("ENDED");
            assertThat(board.isUnfrozen()).isEqualTo(Boolean.TRUE.equals(unfrozen));
        }
    }

}
