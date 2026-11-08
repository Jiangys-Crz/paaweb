package com.cwnupaa.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.vo.RankItemVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class ExternalBoardSubmissionOrderingTests {
    private final LocalDateTime start = LocalDateTime.of(2026, 1, 1, 9, 0);

    @ParameterizedTest
    @CsvSource({"XCPC,personal", "XCPC,team", "LANQIAO,personal", "GPLT,personal", "GPLT,team"})
    @SuppressWarnings("unchecked")
    void everySubmissionPrecedesInactiveEntriesWithoutChangingScoresOrReplay(String type, String scope) throws Exception {
        var service = new ExternalBoardService();
        var events = mock(EventMapper.class);
        var boards = mock(ExternalBoardMapper.class);
        var problems = mock(EventProblemMapper.class);
        var submissions = mock(SubmitRecordMapper.class);
        var personal = mock(SignupPersonalMapper.class);
        var teams = mock(SignupTeamMapper.class);
        var details = mock(SignupTeamDetailMapper.class);
        var users = mock(UserMapper.class);
        ReflectionTestUtils.setField(service, "eventMapper", events);
        ReflectionTestUtils.setField(service, "externalBoardMapper", boards);
        ReflectionTestUtils.setField(service, "eventProblemMapper", problems);
        ReflectionTestUtils.setField(service, "submitRecordMapper", submissions);
        ReflectionTestUtils.setField(service, "signupPersonalMapper", personal);
        ReflectionTestUtils.setField(service, "signupTeamMapper", teams);
        ReflectionTestUtils.setField(service, "signupTeamDetailMapper", details);
        ReflectionTestUtils.setField(service, "userMapper", users);
        var event = new Event();
        event.setEventId(1L);
        event.setType(type);
        event.setIsTeam("GPLT".equals(type) || "team".equals(scope) ? 1 : 0);
        event.setEventStart(start);
        event.setEventEnd(start.plusHours(5));
        when(events.selectById(1L)).thenReturn(event);
        when(boards.selectById(anyLong())).thenAnswer(call -> {
            var board = new ExternalBoard();
            board.setBoardId(call.getArgument(0));
            board.setEventId(1L);
            board.setExternalOpen(true);
            board.setUnfrozen(false);
            board.setFreezeTime(start.plusHours(4));
            board.setMedalConfig("{}");
            return board;
        });
        var problem = new EventProblem();
        problem.setDisplayId("A");
        problem.setScore(100);
        when(problems.selectList(any())).thenReturn(List.of(problem));
        var individuals = new ArrayList<SignupPersonal>();
        var teamSignups = new ArrayList<SignupTeam>();
        var members = new ArrayList<SignupTeamDetail>();
        var people = new ArrayList<Users>();
        for (long id = 1; id <= 6; id++) {
            var individual = new SignupPersonal();
            individual.setUserId(100 + id);
            individual.setIsRanking(1);
            individual.setType("official");
            individuals.add(individual);
            var team = new SignupTeam();
            team.setSignupId(id);
            team.setIsRanking(1);
            team.setType("GPLT".equals(type) ? "星芒初绽" : "official");
            teamSignups.add(team);
            var member = new SignupTeamDetail();
            member.setSignupTeamId(id);
            member.setUserId(100 + id);
            members.add(member);
            var user = new Users();
            user.setUserId(100 + id);
            user.setRealName("选手" + id);
            people.add(user);
        }
        when(personal.selectList(any())).thenReturn(individuals);
        when(teams.selectList(any())).thenReturn(teamSignups);
        when(details.selectList(any())).thenReturn(members);
        when(users.selectBatchIds(any())).thenReturn(people);
        List<SubmitRecord> records = new ArrayList<>(List.of(
                submission(2, "Wrong Answer", 0, 1),
                submission(3, "Compile Error", 0, 2),
                submission(5, "Accepted", 100, 5),
                submission(6, "Accepted", 80, 10),
                submission(4, "Accepted", 100, 245)));
        if ("GPLT".equals(type)) {
            // Member-only submissions must also count as team activity.
            records.forEach(record -> record.setSignupTeamId(null));
            if ("team".equals(scope)) {
                // A team-account submission can have no individual user mapping.
                records.get(4).setUserId(null);
                records.get(4).setSignupTeamId(4L);
            }
        }
        when(submissions.selectList(any())).thenAnswer(call -> {
            QueryWrapper<SubmitRecord> query = call.getArgument(0);
            query.getSqlSegment();
            var params = query.getParamNameValuePairs().values();
            if (!params.contains(7L)) return List.of();
            LocalDateTime replay = params.stream().filter(LocalDateTime.class::isInstance)
                    .map(LocalDateTime.class::cast).filter(t -> t.isAfter(start)).min(LocalDateTime::compareTo).orElse(start.plusHours(5));
            return records.stream().filter(record -> !record.getSubmitTime().isAfter(replay)).toList();
        });

        boolean teamRows = "team".equals(scope);
        long offset = teamRows ? 0 : 100;
        var board = service.getBoardDetail(7L, null, scope, null, null);
        List<RankItemVO> rows = board.getRows();
        if ("XCPC".equals(type)) {
            // An AC after the freeze must not appear in public problem aggregates.
            assertThat(board.getProblems().get(0).getAccepted()).isEqualTo(2);
            assertThat(board.getProblems().get(0).getSubmitted()).isEqualTo(4);
        }
        assertThat(rows).hasSize(6);
        assertThat(rows.subList(0, 2)).extracting(RankItemVO::getItemId).containsExactly(offset + 5, offset + 6);
        assertThat(rows.get(5).getItemId()).isEqualTo(offset + 1);
        assertThat(rows.subList(0, 5)).allMatch(RankItemVO::getHasSubmission);
        assertThat(rows.get(5).getHasSubmission()).isFalse();
        RankItemVO compileError = rows.stream().filter(row -> row.getItemId() == offset + 3).findFirst().orElseThrow();
        assertThat(compileError.getTotalScore()).isZero();
        assertThat(compileError.getPenaltyMillis() == null ? 0L : compileError.getPenaltyMillis()).isZero();
        if ("LANQIAO".equals(type)) assertThat(compileError.getProblemStatus().get("A").getScore()).isZero();
        else assertThat(compileError.getProblemStatus()).isEmpty();
        RankItemVO frozen = rows.stream().filter(row -> row.getItemId() == offset + 4).findFirst().orElseThrow();
        assertThat(frozen.getTotalScore()).isZero();
        if ("XCPC".equals(type)) {
            assertThat(frozen.getSolved()).isZero();
            assertThat(frozen.getProblemStatus().get("A").getStatus()).isEqualTo("PENDING");
            assertThat(rows.get(0).getPenaltyMillis()).isEqualTo(5 * 60 * 1000L);
        } else {
            assertThat(rows.get(0).getTotalScore()).isEqualTo(100);
            assertThat(rows.get(1).getTotalScore()).isEqualTo(80);
        }
        assertThat(new ObjectMapper().writeValueAsString(rows.get(0))).doesNotContain("hasSubmission");

        if ("LANQIAO".equals(type)) {
            // Later lower scores and CE must replace the old AC, not fall back.
            records.add(submission(5, "Wrong Answer", 20, 30));
            assertThat(service.getBoardDetail(7L, null, scope, null, null).getRows())
                    .filteredOn(row -> row.getItemId() == 105L).extracting(RankItemVO::getTotalScore).containsExactly(20);
            records.add(submission(5, "Compile Error", 100, 31));
            assertThat(service.getBoardDetail(7L, null, scope, null, null).getRows())
                    .filteredOn(row -> row.getItemId() == 105L).extracting(RankItemVO::getTotalScore).containsExactly(0);
            var upstreamLast = submission(6, "Wrong Answer", 30, 40);
            upstreamLast.setRid("proctor:z");
            var arrivedLast = submission(6, "Wrong Answer", 70, 40);
            arrivedLast.setRid("proctor:a");
            records.add(upstreamLast);
            records.add(arrivedLast);
            records.add(submission(6, "Cancelled", 0, 41));
            assertThat(service.getBoardDetail(7L, null, scope, null, null).getRows())
                    .filteredOn(row -> row.getItemId() == 106L).extracting(RankItemVO::getTotalScore).containsExactly(30);
        }

        long replay = start.plusMinutes(1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        List<RankItemVO> replayRows = service.getBoardDetail(7L, replay, scope, null, null).getRows();
        assertThat(replayRows.get(0).getItemId()).isEqualTo(offset + 2);
        assertThat(replayRows).filteredOn(RankItemVO::getHasSubmission).hasSize(1);
        assertThat(service.getBoardDetail(8L, null, scope, null, null).getRows())
                .noneMatch(RankItemVO::getHasSubmission);
    }

    private SubmitRecord submission(long id, String verdict, int score, int minute) {
        var record = new SubmitRecord();
        record.setUserId(100 + id);
        record.setSignupTeamId(id);
        record.setProblemId("A");
        record.setSubmitTime(start.plusMinutes(minute));
        record.setResult(verdict);
        record.setScore(score);
        return record;
    }
}
