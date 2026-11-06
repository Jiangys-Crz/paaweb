package com.cwnupaa.backend.service;

import com.cwnupaa.backend.dto.ResolverSettingsDTO;
import com.cwnupaa.backend.vo.BoardVO;
import com.cwnupaa.backend.vo.RankItemVO;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ResolverEngineServiceTests {
    @Test void officialEngineRevealsOnlyFrozenSubmissionsAndUsesConfiguredWinners() {
        var start = LocalDateTime.of(2026, 1, 1, 9, 0);
        var board = new BoardVO(); board.setTitle("Contest"); board.setStartTime(start); board.setEndTime(start.plusHours(5)); board.setFreezeTime(start.plusHours(4).toString());
        var problem = new BoardVO.ProblemInfo(); problem.setId("A"); problem.setName("A"); board.setProblems(List.of(problem));
        var first = new RankItemVO(); first.setItemId(1L); first.setName("First"); first.setMedal("Gold");
        var second = new RankItemVO(); second.setItemId(2L); second.setName("Second"); board.setRows(List.of(first, second));
        var before = submission(10,1,"A", 60_000,"Wrong Answer", false);
        var boundary = submission(11,1,"A", 4*60*60*1000,"Compile Error", false);
        var frozen = submission(12,1,"A", 4*60*60*1000+1,"Accepted", true);
        List<Map<String,Object>> steps = new ResolverEngineService().generate(board, board, List.of(before, boundary, frozen));
        assertThat(steps).extracting(s -> s.get("type")).contains("selectTeam", "selectProblem", "judge", "pause", "award", "complete");
        assertThat(steps).filteredOn(s -> "judge".equals(s.get("type")))
                .extracting(s -> s.get("submissionId")).containsExactly(12L);
        assertThat(steps).filteredOn(s -> "award".equals(s.get("type")))
                .anySatisfy(s -> { assertThat(s.get("participantId")).isEqualTo(1L); assertThat(s.get("medal")).isEqualTo("Gold"); });
        assertThat(steps.get(steps.size()-1).get("type")).isEqualTo("complete");
    }
    @Test void consecutiveWrongAndAcceptedSubmissionsRevealInOfficialOrderOnceEach() {
        var start = LocalDateTime.of(2026, 1, 1, 9, 0);
        var board = new BoardVO(); board.setTitle("Contest"); board.setStartTime(start); board.setEndTime(start.plusHours(5)); board.setFreezeTime(start.plusHours(4).toString());
        var problem = new BoardVO.ProblemInfo(); problem.setId("A"); problem.setName("A"); board.setProblems(List.of(problem));
        var participant = new RankItemVO(); participant.setItemId(9L); participant.setName("Team"); board.setRows(List.of(participant));
        List<Map<String,Object>> submissions = List.of(
                submission(51,9,"A", 4*60*60*1000L+1,"Wrong Answer", true),
                submission(52,9,"A", 4*60*60*1000L+2,"Compile Error", true),
                submission(53,9,"A", 4*60*60*1000L+3,"Accepted", true));
        List<Map<String,Object>> steps = new ResolverEngineService().generate(board, board, submissions);
        assertThat(steps).filteredOn(s -> "judge".equals(s.get("type")))
                .extracting(s -> s.get("submissionId")).containsExactly(52L, 51L, 53L);
        assertThat(steps).filteredOn(s -> "selectSubmission".equals(s.get("type")))
                .anySatisfy(s -> assertThat(s.get("submissionId")).isEqualTo(51L));
    }

    @Test void defaultFreezeBeforeShortContestIncludesSubmissionAtStart() {
        var start = LocalDateTime.of(2026, 1, 1, 9, 0);
        var board = new BoardVO(); board.setTitle("Short"); board.setStartTime(start); board.setEndTime(start.plusMinutes(30));
        var problem = new BoardVO.ProblemInfo(); problem.setId("A"); problem.setName("A"); board.setProblems(List.of(problem));
        var participant = new RankItemVO(); participant.setItemId(9L); participant.setName("Team"); board.setRows(List.of(participant));
        var steps = new ResolverEngineService().generate(board, board, List.of(submission(51,9,"A",0,"Accepted",true)));
        assertThat(steps).filteredOn(s -> "judge".equals(s.get("type")))
                .extracting(s -> s.get("submissionId")).containsExactly(51L);
    }

    @Test void wrongAttemptAddsTwentyMinutesAndDeterminesOfficialFinalOrdering() {
        var start = LocalDateTime.of(2026, 1, 1, 9, 0);
        var board = new BoardVO(); board.setTitle("Penalty"); board.setStartTime(start); board.setEndTime(start.plusHours(5)); board.setFreezeTime(start.plusHours(3).toString());
        var a = new BoardVO.ProblemInfo(); a.setId("A"); a.setName("A");
        var b = new BoardVO.ProblemInfo(); b.setId("B"); b.setName("B"); board.setProblems(List.of(a, b));
        var first = new RankItemVO(); first.setItemId(2L); first.setName("Ahead");
        var second = new RankItemVO(); second.setItemId(1L); second.setName("Behind"); board.setRows(List.of(first, second));
        var contest = new ResolverEngineService().createContest(board, List.of(
                submission(40, 1, "A", 70 * 60_000L, "Accepted", false),
                submission(41, 2, "A", 100 * 60_000L, "Accepted", false),
                submission(42, 1, "B", 100 * 60_000L, "Wrong Answer", false),
                submission(43, 2, "B", 170 * 60_000L, "Accepted", false),
                submission(44, 1, "B", 190 * 60_000L, "Accepted", true)));
        assertThat(contest.getPenaltyTime()).isEqualTo(20 * 60_000L * 60_000L);
        assertThat(contest.getOrderedTeams()).extracting(team -> team.getId()).containsExactly("2", "1");
        assertThat(contest.getStanding(contest.getTeamById("1")).getTime())
                .isGreaterThan(contest.getStanding(contest.getTeamById("2")).getTime());
    }

    @Test void officialModelUsesScaledTimeToPreserveMillisecondOrdering() {
        var start = LocalDateTime.of(2026, 1, 1, 9, 0);
        var board = new BoardVO(); board.setTitle("Precision"); board.setStartTime(start); board.setEndTime(start.plusHours(5)); board.setFreezeTime(start.plusHours(4).toString());
        var a = new BoardVO.ProblemInfo(); a.setId("A"); a.setName("A"); board.setProblems(List.of(a));
        var slower = new RankItemVO(); slower.setItemId(1L); slower.setName("A slower");
        var faster = new RankItemVO(); faster.setItemId(2L); faster.setName("B faster"); board.setRows(List.of(slower, faster));
        var contest = new ResolverEngineService().createContest(board, List.of(
                submission(51, 1, "A", 60_002L, "Accepted", false),
                submission(52, 2, "A", 60_001L, "Accepted", false)));
        assertThat(contest.getOrderedTeams()).extracting(team -> team.getId()).containsExactly("2", "1");
    }

    @Test void scaledPenaltyAndMillisecondsMatchExistingBoardOrder() {
        var start = LocalDateTime.of(2026, 1, 1, 9, 0);
        var board = new BoardVO(); board.setTitle("Penalty precision"); board.setStartTime(start); board.setEndTime(start.plusHours(5)); board.setFreezeTime(start.plusHours(4).toString());
        var a = new BoardVO.ProblemInfo(); a.setId("A"); a.setName("A"); board.setProblems(List.of(a));
        var second = new RankItemVO(); second.setItemId(1L); second.setName("Penalty");
        var first = new RankItemVO(); first.setItemId(2L); first.setName("Clean"); board.setRows(List.of(second, first));
        var contest = new ResolverEngineService().createContest(board, List.of(
                submission(1, 1, "A", 30_000, "Wrong Answer", false),
                submission(2, 1, "A", 60_002, "Accepted", false),
                submission(3, 2, "A", 1_260_001, "Accepted", false)));
        assertThat(contest.getOrderedTeams()).extracting(team -> team.getId()).containsExactly("2", "1");
        assertThat(contest.getStanding(contest.getTeamById("1")).getTime()
                - contest.getStanding(contest.getTeamById("2")).getTime()).isEqualTo(60_000L);
    }

    @Test void starCanOutrankOfficialMedalistWithoutReceivingTheirAward() {
        var start = LocalDateTime.of(2026, 1, 1, 9, 0);
        var board = new BoardVO(); board.setTitle("Stars"); board.setStartTime(start); board.setEndTime(start.plusHours(5)); board.setFreezeTime(start.plusHours(4).toString());
        var a = new BoardVO.ProblemInfo(); a.setId("A"); a.setName("A"); board.setProblems(List.of(a));
        var star = new RankItemVO(); star.setItemId(1L); star.setName("Star"); star.setIsStar(true);
        var gold = new RankItemVO(); gold.setItemId(2L); gold.setName("Gold"); gold.setMedal("Gold"); board.setRows(List.of(star, gold));
        var submissions = List.of(submission(51, 1, "A", 4 * 60 * 60_000L + 60_000, "Accepted", true),
                submission(52, 2, "A", 4 * 60 * 60_000L + 120_000, "Accepted", true));
        var engine = new ResolverEngineService();
        var contest = engine.createContest(board, submissions);
        assertThat(contest.getOrderedTeams()).extracting(team -> team.getId()).containsExactly("1", "2");
        assertThat(contest.getAwards()).hasSize(2);
        assertThat(contest.getAwards()).allSatisfy(award -> assertThat(award.getTeamIds()).containsExactly("2"));
        var steps = engine.generate(board, board, submissions);
        assertThat(steps).filteredOn(s -> "award".equals(s.get("type")))
                .anySatisfy(s -> { assertThat(s.get("participantId")).isEqualTo(2L); assertThat(s.get("medal")).isEqualTo("Gold"); });
        assertThat(steps).filteredOn(s -> "award".equals(s.get("type")))
                .noneMatch(s -> Long.valueOf(1L).equals(s.get("participantId")));
    }

    @Test void equalScoresRetainProvidedFrozenBoardOrder() {
        var start = LocalDateTime.of(2026, 1, 1, 9, 0);
        var board = new BoardVO(); board.setTitle("Ties"); board.setStartTime(start); board.setEndTime(start.plusHours(5)); board.setFreezeTime(start.plusHours(4).toString());
        var a = new BoardVO.ProblemInfo(); a.setId("A"); a.setName("A"); board.setProblems(List.of(a));
        var first = new RankItemVO(); first.setItemId(2L); first.setName("Z first");
        var second = new RankItemVO(); second.setItemId(1L); second.setName("A second"); board.setRows(List.of(first, second));
        var contest = new ResolverEngineService().createContest(board, List.of(
                submission(51, 1, "A", 60_000L, "Accepted", false),
                submission(52, 2, "A", 60_000L, "Accepted", false)));
        assertThat(contest.getOrderedTeams()).extracting(team -> team.getId()).containsExactly("2", "1");
    }

    @Test void externalTelemetryIsExplicitlyDisabled() {
        new ResolverEngineService();
        assertThat(io.sentry.Sentry.isEnabled()).isFalse();
    }

    @Test void frozenFlagDisambiguatesSubMillisecondBoundaryWithoutChangingPenalty() {
        var start = LocalDateTime.of(2026, 1, 1, 9, 0);
        var board = new BoardVO(); board.setTitle("Boundary"); board.setStartTime(start); board.setEndTime(start.plusHours(5)); board.setFreezeTime(start.plusHours(4).toString());
        var a = new BoardVO.ProblemInfo(); a.setId("A"); a.setName("A"); board.setProblems(List.of(a));
        var row = new RankItemVO(); row.setItemId(1L); row.setName("Team"); board.setRows(List.of(row));
        var steps = new ResolverEngineService().generate(board, board, List.of(
                submission(10, 1, "A", 4 * 60 * 60_000L, "Accepted", true)));
        assertThat(steps).filteredOn(s -> "judge".equals(s.get("type")))
                .extracting(s -> s.get("submissionId")).containsExactly(10L);
    }

    @Test void singleStepInsertsPausesBetweenTeamsWhileContinuousModeKeepsNativeFlow() {
        var start = LocalDateTime.of(2026, 1, 1, 9, 0);
        var board = new BoardVO(); board.setTitle("Steps"); board.setStartTime(start); board.setEndTime(start.plusHours(5)); board.setFreezeTime(start.plusHours(4).toString());
        var a = new BoardVO.ProblemInfo(); a.setId("A"); a.setName("A"); board.setProblems(List.of(a));
        List<RankItemVO> rows = new ArrayList<>(); List<Map<String,Object>> submissions = new ArrayList<>();
        for (long id = 1; id <= 6; id++) {
            var row = new RankItemVO(); row.setItemId(id); row.setName("Team " + id); rows.add(row);
            submissions.add(submission(id, id, "A", 4 * 60 * 60_000L + id * 60_000, "Accepted", true));
        }
        board.setRows(rows);
        var engine = new ResolverEngineService();
        var oneAtATime = engine.generate(board, board, submissions, true);
        var continuous = engine.generate(board, board, submissions, false);
        assertThat(oneAtATime.stream().filter(s -> "pause".equals(s.get("type"))).count())
                .isGreaterThan(continuous.stream().filter(s -> "pause".equals(s.get("type"))).count());
        Set<Object> teamsBetweenPauses = new HashSet<>();
        for (var step : oneAtATime) {
            if ("pause".equals(step.get("type"))) {
                assertThat(teamsBetweenPauses).hasSizeLessThanOrEqualTo(1);
                teamsBetweenPauses.clear();
            } else if ("judge".equals(step.get("type"))) teamsBetweenPauses.add(step.get("participantId"));
        }
        assertThat(teamsBetweenPauses).hasSizeLessThanOrEqualTo(1);
        assertThat(oneAtATime).filteredOn(s -> "judge".equals(s.get("type"))).hasSize(6);
        assertThat(engine.generate(board, board, submissions)).isEqualTo(oneAtATime);
    }

    @Test void officialAwardsRespectPerBoardSettingsAndCustomLabels() {
        var start = LocalDateTime.of(2026, 1, 1, 9, 0);
        var board = new BoardVO(); board.setTitle("Awards"); board.setStartTime(start); board.setEndTime(start.plusHours(5)); board.setFreezeTime(start.plusHours(4).toString());
        var a = new BoardVO.ProblemInfo(); a.setId("A"); a.setName("A"); board.setProblems(List.of(a));
        var gold = new RankItemVO(); gold.setItemId(1L); gold.setName("Gold"); gold.setMedal("Gold"); board.setRows(List.of(gold));
        var settings = new ResolverSettingsDTO(); settings.getAwards().setGoldLabel("金奖");
        settings.getAwards().setFirstToSolve(true); settings.getAwards().setSolvedCount(true);
        var custom = new ResolverSettingsDTO.CustomAward(); custom.setCitation("Special Prize"); custom.setParticipantIds(List.of(1L)); custom.setDisplayMode("detail");
        settings.getAwards().setCustomAwards(List.of(custom));
        var submissions = List.of(submission(11, 1, "A", 4*60*60_000L+1, "Accepted", true));
        var engine = new ResolverEngineService();
        var steps = engine.generate(board, board, submissions, true, settings);
        assertThat(steps).filteredOn(s -> "award".equals(s.get("type"))).anySatisfy(s -> {
            assertThat(s.get("citations").toString()).contains("金奖", "Special Prize");
            assertThat(s.get("medal")).isEqualTo("Gold");
            assertThat((List<?>) s.get("awards")).anyMatch(entry -> Map.of("citation", "金奖", "type", "Medal").equals(entry)).anyMatch(entry -> Map.of("citation", "Special Prize", "type", "Other").equals(entry));
        });
        settings.getAwards().setShowMedals(false); settings.getAwards().setShowPodium(false); settings.getAwards().setFirstToSolve(false); settings.getAwards().setSolvedCount(false);
        settings.getAwards().setCustomAwards(List.of());
        assertThat(engine.generate(board, board, submissions, true, settings)).noneMatch(s -> "award".equals(s.get("type")));
        custom.setParticipantIds(List.of(999L)); settings.getAwards().setCustomAwards(List.of(custom));
        assertThatThrownBy(() -> engine.generate(board, board, submissions, true, settings)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void officialListAndPhotoAwardsExposeTheChosenTeamsAndCaption() {
        var start = LocalDateTime.of(2026, 1, 1, 9, 0);
        var board = new BoardVO(); board.setTitle("Group awards"); board.setStartTime(start); board.setEndTime(start.plusHours(5)); board.setFreezeTime(start.plusHours(4).toString());
        var a = new BoardVO.ProblemInfo(); a.setId("A"); a.setName("A"); board.setProblems(List.of(a));
        var one = new RankItemVO(); one.setItemId(1L); one.setName("One");
        var two = new RankItemVO(); two.setItemId(2L); two.setName("Two"); board.setRows(List.of(one, two));
        var submissions = List.of(submission(1, 1, "A", 4*60*60_000L+1, "Accepted", true));
        for (String mode : List.of("list", "photos")) {
            var settings = new ResolverSettingsDTO(); settings.getAwards().setShowMedals(false);
            var custom = new ResolverSettingsDTO.CustomAward(); custom.setCitation("Special teams"); custom.setDisplayMode(mode); custom.setParticipantIds(List.of(1L, 2L));
            settings.getAwards().setCustomAwards(List.of(custom));
            var steps = new ResolverEngineService().generate(board, board, submissions, true, settings);
            assertThat(steps).filteredOn(s -> "listAward".equals(s.get("type"))).anySatisfy(s -> {
                assertThat(s.get("citation")).isEqualTo("Special teams");
                assertThat(s.get("participantIds")).isEqualTo(List.of(1L, 2L));
                assertThat(s.get("photos")).isEqualTo(mode.equals("photos"));
            });
            assertThat(steps).anyMatch(s -> "scrollTeamList".equals(s.get("type")) && Boolean.TRUE.equals(s.get("top")));
            if (mode.equals("list")) assertThat(steps).anyMatch(s -> "scrollTeamList".equals(s.get("type")) && Boolean.FALSE.equals(s.get("top")));
            assertThat(steps).anyMatch(s -> "screen".equals(s.get("type")) && (mode.equals("photos") ? "TEAM_LIST_PHOTO" : "TEAM_LIST").equals(s.get("screen")));
        }
    }

    @Test void medalGroupsUseOfficialListsWithoutLosingIndividualAwards() {
        var start = LocalDateTime.of(2026, 1, 1, 9, 0);
        var board = new BoardVO(); board.setTitle("Medals"); board.setStartTime(start); board.setEndTime(start.plusHours(5)); board.setFreezeTime(start.plusHours(4).toString());
        var p = new BoardVO.ProblemInfo(); p.setId("A"); p.setName("A"); board.setProblems(List.of(p));
        var rows = new ArrayList<RankItemVO>(); var submissions = new ArrayList<Map<String,Object>>();
        var medals = List.of("Gold", "Gold", "Silver", "Silver", "Bronze", "Bronze");
        for (int i = 0; i < medals.size(); i++) {
            var row = new RankItemVO(); row.setItemId((long) i + 1); row.setName(medals.get(i)); row.setMedal(medals.get(i)); rows.add(row);
            submissions.add(submission(i + 1, i + 1, "A", 4 * 60 * 60_000L + (i + 1) * 60_000L, "Accepted", true));
        }
        board.setRows(rows);
        var steps = new ResolverEngineService().generate(board, board, submissions);
        assertThat(steps).filteredOn(s -> "award".equals(s.get("type"))).extracting(s -> s.get("medal"))
                .contains("Gold", "Silver", "Bronze");
        assertThat(steps).filteredOn(s -> "listAward".equals(s.get("type"))).extracting(s -> s.get("medal"))
                .containsExactly("Bronze", "Silver", "Gold");
        assertThat(steps).filteredOn(s -> "listAward".equals(s.get("type"))).allSatisfy(s -> {
            assertThat(s.get("awardType")).isEqualTo("Medal"); assertThat(s.get("photos")).isEqualTo(false); assertThat(s.get("after")).isEqualTo(true);
        });
        List<String> ceremony = steps.stream().filter(s -> ("award".equals(s.get("type")) || "listAward".equals(s.get("type"))) && s.get("medal") != null)
                .map(s -> s.get("type") + ":" + s.get("medal") + ("award".equals(s.get("type")) ? ":" + s.get("participantId") : ""))
                .toList();
        assertThat(ceremony).containsExactly("award:Bronze:6", "award:Bronze:5", "listAward:Bronze",
                "award:Silver:4", "award:Silver:3", "listAward:Silver",
                "award:Gold:2", "award:Gold:1", "listAward:Gold");
    }

    @Test void sameTeamFirstSolvesAreCombinedInProblemOrder() {
        var start = LocalDateTime.of(2026, 1, 1, 9, 0);
        var board = new BoardVO(); board.setTitle("First solves"); board.setStartTime(start); board.setEndTime(start.plusHours(5)); board.setFreezeTime(start.plusHours(4).toString());
        var problems = new ArrayList<BoardVO.ProblemInfo>(); var submissions = new ArrayList<Map<String,Object>>();
        int id = 0;
        for (String label : List.of("A", "B", "K")) {
            var p = new BoardVO.ProblemInfo(); p.setId(label); p.setName(label); problems.add(p);
            submissions.add(submission(++id, 1, label, 4 * 60 * 60_000L + id * 60_000L, "Accepted", true));
        }
        board.setProblems(problems); var row = new RankItemVO(); row.setItemId(1L); row.setName("Team"); row.setMedal("Gold"); board.setRows(List.of(row));
        var settings = new ResolverSettingsDTO(); settings.getAwards().setFirstToSolve(true);
        var steps = new ResolverEngineService().generate(board, board, submissions, true, settings);
        assertThat(steps).filteredOn(s -> "award".equals(s.get("type"))).anySatisfy(s -> {
            var citations = ((List<?>) s.get("citations")).stream().map(String::valueOf).toList();
            assertThat(citations).contains("First Solve A, B and K");
            assertThat(citations).filteredOn(c -> c.startsWith("First Solve")).hasSize(1);
        });
    }

    @Test void bestFemaleAwardUsesOfficialRanksIncludesTiesAndExcludesStars() {
        var start = LocalDateTime.of(2026, 1, 1, 9, 0);
        var board = new BoardVO(); board.setTitle("Women"); board.setStartTime(start); board.setEndTime(start.plusHours(5)); board.setFreezeTime(start.plusHours(4).toString()); board.setIsTeam(1);
        var p = new BoardVO.ProblemInfo(); p.setId("A"); p.setName("A"); board.setProblems(List.of(p));
        var star = new RankItemVO(); star.setItemId(1L); star.setName("Star"); star.setIsGirl(true); star.setIsStar(true); star.setRank(-1);
        var a = new RankItemVO(); a.setItemId(2L); a.setName("A"); a.setIsGirl(true); a.setRank(2);
        var b = new RankItemVO(); b.setItemId(3L); b.setName("B"); b.setIsGirl(true); b.setRank(2);
        var later = new RankItemVO(); later.setItemId(4L); later.setName("C"); later.setIsGirl(true); later.setRank(4);
        var man = new RankItemVO(); man.setItemId(5L); man.setName("D"); man.setIsGirl(false); man.setRank(1);
        board.setRows(List.of(star, man, a, b, later));
        var submissions = List.of(submission(1, 1, "A", 60_000, "Accepted", false), submission(2, 2, "A", 120_000, "Accepted", false),
                submission(3, 3, "A", 120_000, "Accepted", false), submission(4, 4, "A", 180_000, "Accepted", false), submission(5, 5, "A", 90_000, "Accepted", false));
        var settings = new ResolverSettingsDTO(); settings.getAwards().setShowMedals(false); settings.getAwards().setShowPodium(false); settings.getAwards().setBestFemaleTeam(true);
        var engine = new ResolverEngineService();
        var steps = engine.generate(board, board, submissions, true, settings);
        assertThat(steps).filteredOn(s -> "award".equals(s.get("type")) && ((List<?>) s.get("citations")).contains("最佳女队 Best Female"))
                .extracting(s -> s.get("participantId")).containsExactlyInAnyOrder(2L, 3L);
        settings.getAwards().setBestFemaleTeam(false);
        assertThat(engine.generate(board, board, submissions, true, settings)).noneMatch(s -> "award".equals(s.get("type")));
        settings.getAwards().setBestFemaleTeam(true); board.setIsTeam(0);
        assertThat(engine.generate(board, board, submissions, true, settings))
                .filteredOn(s -> "award".equals(s.get("type")) && ((List<?>) s.get("citations")).contains("最佳女队 Best Female"))
                .extracting(s -> s.get("participantId")).containsExactlyInAnyOrder(2L, 3L);
        board.setIsTeam(1); a.setIsGirl(false); b.setIsGirl(false); later.setIsGirl(false);
        assertThat(engine.generate(board, board, submissions, true, settings)).noneMatch(s -> "award".equals(s.get("type")));
    }

    @Test void nameTemplatesAreBoundedAndOnlyAllowDocumentedTokens() {
        var settings = new ResolverSettingsDTO();
        assertThat(settings.getDisplay().getNameTemplate()).isEqualTo("{org.name}: {team.name}");
        settings.getDisplay().setNameTemplate("{org.formal_name} {team.display_name} {team.english_name}");
        settings.validate();
        for (String invalid : List.of("{team.bad}", "{team.name", "team}", "{team.{name}}", "x".repeat(201))) {
            settings.getDisplay().setNameTemplate(invalid);
            assertThatThrownBy(settings::validate).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        }
    }

    @Test void emptyContestProducesCompleteWithoutPresentation() {
        var board = new BoardVO(); board.setRows(List.of());
        assertThat(new ResolverEngineService().generate(board, board, List.of())).containsExactly(Map.of("type", "complete"));
    }
    private static Map<String,Object> submission(long id,long participant,String problem,long time,String result,boolean frozen) {
        return Map.of("submissionId", id, "participantId", participant, "problemId", problem, "timeMillis", time, "result", result, "frozen", frozen);
    }
}
