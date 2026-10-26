package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cwnupaa.backend.entity.Event;
import com.cwnupaa.backend.entity.SubmitRecord;
import com.cwnupaa.backend.mapper.EventMapper;
import com.cwnupaa.backend.mapper.EventProblemMapper;
import com.cwnupaa.backend.mapper.SubmitRecordMapper;
import com.cwnupaa.backend.service.ExternalBoardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExternalBoardCodeAccessTests {
    private static final String SOURCE = "SOURCE_MUST_NOT_LEAK_BEFORE_UNFREEZE";
    private MockMvc mvc;
    private Event event;
    private SubmitRecord record;
    private SubmitRecordMapper submissions;

    @BeforeEach void setup() {
        var service = new ExternalBoardService();
        var events = mock(EventMapper.class);
        submissions = mock(SubmitRecordMapper.class);
        ReflectionTestUtils.setField(service, "eventMapper", events);
        com.cwnupaa.backend.support.ExternalBoardFixtures.attach(service, events);
        ReflectionTestUtils.setField(service, "submitRecordMapper", submissions);
        ReflectionTestUtils.setField(service, "eventProblemMapper", mock(EventProblemMapper.class));
        event = new Event();
        event.setExternalOpen(true);
        event.setEventEnd(LocalDateTime.now().minusDays(1));
        record = new SubmitRecord();
        record.setEventId(7L); record.setBoardId(7L);
        record.setSubmitRecordId(100L);
        record.setProblemId("A");
        record.setSubmitTime(event.getEventEnd().minusHours(2));
        record.setResult("Accepted");
        record.setCodeContent(SOURCE);
        record.setLanguage("cpp");
        when(events.selectById(7L)).thenReturn(event);
        when(submissions.selectById(100L)).thenReturn(record);
        var controller = new ExternalBoardController();
        ReflectionTestUtils.setField(controller, "externalBoardService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test void endedButLockedAndUnsetStatesReturnNoCodeInHttpResponse() throws Exception {
        for (Boolean unfrozen : new Boolean[]{false, null}) {
            event.setUnfrozen(unfrozen);
            mvc.perform(get("/leaderboard/submission/100/code"))
                .andExpect(status().isOk()) // Existing API reports business failures in its JSON envelope.
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value(containsString("unfrozen")))
                .andExpect(jsonPath("$.data.content").doesNotExist())
                .andExpect(content().string(not(containsString(SOURCE))));
        }
    }

    @Test void actualEndTimeMustHavePassedEvenIfUnfrozenIsTrue() throws Exception {
        event.setUnfrozen(true);
        event.setActualEndTime(LocalDateTime.now().plusDays(1));
        mvc.perform(get("/leaderboard/submission/100/code"))
            .andExpect(jsonPath("$.code").value(500))
            .andExpect(jsonPath("$.msg").value(containsString("running")))
            .andExpect(jsonPath("$.data.content").doesNotExist())
            .andExpect(content().string(not(containsString(SOURCE))));
    }

    @Test void onlyEndedAndUnfrozenContestReturnsSource() throws Exception {
        event.setUnfrozen(true);
        mvc.perform(get("/leaderboard/submission/100/code"))
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.content").value(SOURCE))
            .andExpect(jsonPath("$.data.language").value("cpp"));
    }

    @Test @SuppressWarnings("unchecked")
    void submissionHistoryDoesNotIncludeSourceEvenWhenRecordContainsIt() throws Exception {
        event.setUnfrozen(false);
        when(submissions.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<SubmitRecord> page = invocation.getArgument(0);
            page.setRecords(List.of(record));
            page.setTotal(1);
            return page;
        });
        mvc.perform(get("/leaderboard/submissions/7"))
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.records[0].submissionId").value(100))
            .andExpect(jsonPath("$.data.records[0].codeContent").doesNotExist())
            .andExpect(jsonPath("$.data.records[0].content").doesNotExist())
            .andExpect(content().string(not(containsString(SOURCE))));
    }
}
