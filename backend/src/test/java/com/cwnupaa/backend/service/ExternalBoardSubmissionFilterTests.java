package com.cwnupaa.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cwnupaa.backend.entity.Event;
import com.cwnupaa.backend.entity.SubmitRecord;
import com.cwnupaa.backend.mapper.EventMapper;
import com.cwnupaa.backend.mapper.EventProblemMapper;
import com.cwnupaa.backend.mapper.SubmitRecordMapper;
import com.cwnupaa.backend.vo.BoardSubmissionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExternalBoardSubmissionFilterTests {
    private ExternalBoardService service;
    private SubmitRecordMapper submissions;
    private Event event;
    private QueryWrapper<SubmitRecord> capturedQuery;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        service = new ExternalBoardService();
        EventMapper events = mock(EventMapper.class);
        EventProblemMapper problems = mock(EventProblemMapper.class);
        submissions = mock(SubmitRecordMapper.class);
        ReflectionTestUtils.setField(service, "eventMapper", events);
        com.cwnupaa.backend.support.ExternalBoardFixtures.attach(service, events);
        ReflectionTestUtils.setField(service, "eventProblemMapper", problems);
        ReflectionTestUtils.setField(service, "submitRecordMapper", submissions);
        event = new Event();
        event.setExternalOpen(true);
        event.setEventStart(LocalDateTime.of(2026, 10, 14, 9, 0));
        event.setEventEnd(LocalDateTime.of(2026, 10, 14, 14, 0));
        event.setFreezeTime(LocalDateTime.of(2026, 10, 14, 13, 0));
        event.setUnfrozen(false);
        when(events.selectById(7L)).thenReturn(event);
        when(problems.selectList(any())).thenReturn(List.of());
        when(submissions.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            capturedQuery = invocation.getArgument(1);
            return invocation.getArgument(0);
        });
    }

    @Test
    void teamHistoryUsesExactEventProblemAndTeamAndRespectsReplayTime() {
        LocalDateTime replayTime = LocalDateTime.of(2026, 10, 14, 12, 0);
        long timePoint = replayTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        service.getBoardSubmissions(7L, 2, 10, "A", null, null, 42L, null, timePoint);
        assertThat(capturedQuery.getSqlSegment()).contains("board_id =", "problem_id =", "signup_team_id =", "submit_time <=", "ORDER BY submit_time DESC").doesNotContain("user_id =");
        assertThat(capturedQuery.getParamNameValuePairs().values()).containsExactlyInAnyOrder(7L, 42L, replayTime, "A", event.getEventStart(), event.getEventEnd());
    }

    @Test
    void personalHistoryUsesUserIdWithoutMatchingUnrelatedTeamIds() {
        service.getBoardSubmissions(7L, 1, 10, "B", null, null, null, 42L, null);
        assertThat(capturedQuery.getSqlSegment()).contains("board_id =", "problem_id =", "user_id =").doesNotContain("signup_team_id =");
        assertThat(capturedQuery.getParamNameValuePairs().values()).containsExactlyInAnyOrder(7L, 42L, "B", event.getEventStart(), event.getEventEnd());
    }

    @Test
    @SuppressWarnings("unchecked")
    void historyStillHidesFrozenVerdictsAndResourceUsage() {
        SubmitRecord record = new SubmitRecord();
        record.setSubmitRecordId(100L);
        record.setProblemId("A");
        record.setSubmitTime(LocalDateTime.of(2026, 10, 14, 13, 30));
        record.setResult("Accepted");
        when(submissions.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<SubmitRecord> page = invocation.getArgument(0);
            page.setRecords(List.of(record));
            page.setTotal(1);
            return page;
        });
        Map<String, Object> result = service.getBoardSubmissions(7L, 1, 10, "A", null, null, 42L, null, null);
        BoardSubmissionVO submission = ((List<BoardSubmissionVO>) result.get("records")).get(0);
        assertThat(submission.getStatus()).isEqualTo("PENDING");
        assertThat(submission.getTime()).isEqualTo("-");
        assertThat(submission.getMemory()).isEqualTo("-");
        assertThat(submission.getScore()).isNull();
    }
}
