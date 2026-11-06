package com.cwnupaa.backend.service;

import com.cwnupaa.backend.dto.ResolverSettingsDTO;
import com.cwnupaa.backend.vo.BoardVO;
import com.cwnupaa.backend.vo.RankItemVO;
import org.icpc.tools.contest.model.IAward;
import org.icpc.tools.contest.model.ISubmission;
import org.icpc.tools.contest.model.ITeam;
import org.icpc.tools.contest.model.feed.RelativeTime;
import org.icpc.tools.contest.model.internal.*;
import org.icpc.tools.contest.model.resolver.ResolutionUtil.*;
import org.icpc.tools.contest.model.resolver.ResolverLogic;
import org.icpc.tools.contest.model.util.AwardUtil;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import io.sentry.Sentry;

/** Converts authorized XCPC snapshots to ICPC Tools ResolverLogic steps, without a presentation runtime. */
@Service
public class ResolverEngineService {
    private static final String AC = "AC", WRONG = "WA", COMPILE = "CE";
    // ICPC Tools uses integer minutes. Scaling one real millisecond to one engine minute
    // preserves this application's existing millisecond penalty ordering exactly.
    private static final long TIME_SCALE = 60_000L;
    static {
        Sentry.init(options -> { options.setEnabled(false); options.setDsn(""); options.setEnableExternalConfiguration(false); });
    }

    /** Call only after resolver access has been authorized. Nothing here checks a password. */
    public List<Map<String, Object>> generate(BoardVO frozen, BoardVO finalBoard, List<Map<String, Object>> submissions) {
        return generate(frozen, finalBoard, submissions, true);
    }

    public List<Map<String, Object>> generate(BoardVO frozen, BoardVO finalBoard, List<Map<String, Object>> submissions, boolean singleStep) {
        return generate(frozen, finalBoard, submissions, singleStep, new ResolverSettingsDTO());
    }

    public List<Map<String, Object>> generate(BoardVO frozen, BoardVO finalBoard, List<Map<String, Object>> submissions, boolean singleStep, ResolverSettingsDTO settings) {
        if (settings == null) settings = new ResolverSettingsDTO();
        settings.validate();
        if (finalBoard.getRows().isEmpty()) return List.of(Map.of("type", "complete"));
        Contest contest = createContest(finalBoard, submissions, frozen.getRows(), settings);
        if (singleStep) {
            ResolveInfo resolveInfo = new ResolveInfo();
            resolveInfo.setSingleStepRow(Integer.MAX_VALUE);
            contest.add(resolveInfo);
        }
        Map<String, Map<String, Object>> submissionById = new HashMap<>();
        for (Map<String, Object> record : submissions) submissionById.put(String.valueOf(record.get("submissionId")), record);
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> previouslyJudged = null;
        List<ResolutionStep> officialSteps = new ResolverLogic(contest, false).resolveFrom(false);
        for (int index = 0; index < officialSteps.size(); index++) {
            ResolutionStep step = officialSteps.get(index);
            if (step instanceof ContestStateStep state) {
                if (previouslyJudged == null) {
                    previouslyJudged = new HashSet<>();
                    for (ISubmission sub : state.contest.getSubmissions()) {
                        if (!state.contest.isJudged(sub)) continue;
                        previouslyJudged.add(sub.getId());
                        Map<String,Object> original = submissionById.get(sub.getId());
                        // The official engine resolves non-penalty/outlier submissions before the first pause.
                        if (original != null && Boolean.TRUE.equals(original.get("frozen"))) result.add(submissionEvent("judge", original));
                    }
                } else {
                    // This bundled engine emits a state immediately before the affected submission selection.
                    if (index + 1 >= officialSteps.size() || !(officialSteps.get(index + 1) instanceof SubmissionSelectionStep2 selection))
                        throw new IllegalStateException("Unexpected official resolver state transition");
                    ISubmission changed = state.contest.getSubmissionById(selection.submissionId);
                    Map<String, Object> record = submissionById.get(selection.submissionId);
                    if (changed == null || record == null || !state.contest.isJudged(changed) || !previouslyJudged.add(selection.submissionId))
                        throw new IllegalStateException("Invalid official resolver judgement transition");
                    result.add(submissionEvent("judge", record));
                }
            } else if (step instanceof TeamSelectionStep select) {
                List<Long> ids = select.teams == null ? List.of() : select.teams.stream().filter(Objects::nonNull).map(t -> Long.valueOf(t.getId())).toList();
                Map<String,Object> item = event("selectTeam"); item.put("participantIds", ids);
                item.put("participantId", ids.isEmpty() ? null : ids.get(0)); item.put("selectionType", select.type == null ? null : select.type.name()); result.add(item);
            } else if (step instanceof SubmissionSelectionStep select) {
                Map<String,Object> item = event("selectProblem");
                if (select.subInfo == null) { item.put("participantId", null); item.put("problemId", null); }
                else { item.put("participantId", Long.valueOf(select.subInfo.getTeam().getId())); item.put("problemId", contest.getProblems()[select.subInfo.getProblemIndex()].getId()); }
                result.add(item);
            } else if (step instanceof SubmissionSelectionStep2 select) {
                Map<String,Object> item = submissionEvent("selectSubmission", submissionById.get(select.submissionId)); result.add(item);
            } else if (step instanceof ToJudgeStep queue) {
                Map<String,Object> item = event("toJudge"); item.put("submissionIds", queue.submissionIds == null ? List.of() : Arrays.stream(queue.submissionIds).map(Long::valueOf).toList()); result.add(item);
            } else if (step instanceof ScrollStep scroll) {
                Map<String,Object> item = event("scroll"); item.put("row", scroll.row); result.add(item);
            } else if (step instanceof ScrollTeamListStep scroll) {
                Map<String,Object> item = event("scrollTeamList"); item.put("top", scroll.top); result.add(item);
            } else if (step instanceof PresentationStep presentation) {
                Map<String,Object> item = event("screen"); item.put("screen", presentation.p.name()); result.add(item);
            } else if (step instanceof PauseStep pause) {
                Map<String,Object> item = event("pause"); item.put("number", pause.num); result.add(item);
            } else if (step instanceof DelayStep delay) {
                Map<String,Object> item = event("delay"); item.put("kind", String.valueOf(delay.type)); result.add(item);
            } else if (step instanceof AwardStep award) {
                Map<String,Object> item = event("award"); item.put("participantId", Long.valueOf(award.teamId));
                List<String> medals = award.awards.stream().map(ResolverEngineService::medalName).filter(Objects::nonNull).toList();
                List<Map<String, String>> typedAwards = combineFirstSolves(award.awards, contest);
                List<String> citations = typedAwards.stream().map(a -> a.get("citation")).toList();
                item.put("awards", typedAwards); item.put("citations", citations); item.put("citation", citations.isEmpty() ? null : citations.get(0));
                item.put("medals", medals); item.put("medal", medals.isEmpty() ? null : medals.get(0)); result.add(item);
            } else if (step instanceof ListAwardStep award) {
                Map<String,Object> item = event("listAward"); item.put("participantIds", Arrays.stream(award.teams).map(ITeam::getId).map(Long::valueOf).toList());
                String listMedal = summaryMedalName(award.award);
                item.put("citation", award.award.getCitation()); item.put("awardType", listMedal != null ? "Medal" : award.award.getAwardType() == null ? "Other" : award.award.getAwardType().getName());
                item.put("medal", listMedal);
                Map<String, String> selections = new HashMap<>();
                if (award.selections != null) award.selections.forEach((id, selection) -> { if (selection != null) selections.put(id, selection.name()); });
                item.put("selections", selections); item.put("photos", award.photos); item.put("after", award.after); result.add(item);
            }
            // Display-only messages and engine snapshots never leave the server.
        }
        result.add(event("complete"));
        return result;
    }

    Contest createContest(BoardVO finalBoard, List<Map<String, Object>> submissions) {
        return createContest(finalBoard, submissions, finalBoard.getRows(), new ResolverSettingsDTO());
    }

    private Contest createContest(BoardVO finalBoard, List<Map<String, Object>> submissions, List<RankItemVO> frozenRows, ResolverSettingsDTO settings) {
        LocalDateTime start = finalBoard.getStartTime(), end = finalBoard.getEndTime();
        LocalDateTime freeze = finalBoard.getFreezeTime() == null ? end.minusHours(1) : LocalDateTime.parse(finalBoard.getFreezeTime());
        long duration = Duration.between(start, end).toMillis();
        long freezeAt = Duration.between(start, freeze).toMillis();
        if (duration < 0 || freezeAt > duration) throw new IllegalArgumentException("Invalid contest time window");
        // A short contest with default end-minus-one-hour freeze is frozen from its start.
        freezeAt = Math.max(-1, freezeAt);
        Contest contest = new Contest();
        // ICPC Tools freezes at >= boundary; our board freezes strictly after it.
        Info info = new Info(); info.add("name", finalBoard.getTitle());
        long engineDuration = Math.addExact(Math.multiplyExact(duration, TIME_SCALE), 1);
        long engineFreeze = freezeAt < 0 ? 0 : Math.addExact(Math.multiplyExact(freezeAt, TIME_SCALE), 1);
        info.add("duration", RelativeTime.format(engineDuration));
        info.add("scoreboard_freeze_duration", RelativeTime.format(engineDuration - engineFreeze));
        info.add("penalty_time", String.valueOf(20 * TIME_SCALE)); contest.add(info);

        Map<Long, Integer> stableOrder = new HashMap<>();
        for (int i = 0; i < frozenRows.size(); i++) stableOrder.put(frozenRows.get(i).getItemId(), i);
        int fallbackOrder = frozenRows.size();
        for (RankItemVO row : finalBoard.getRows()) {
            Team team = new Team(); team.add("id", String.valueOf(row.getItemId()));
            // Only used for ICPC Tools tie sorting; the browser displays the real BoardVO name.
            int order = stableOrder.getOrDefault(row.getItemId(), fallbackOrder++);
            team.add("name", String.format(Locale.ROOT, "%010d", order));
            team.add("label", String.valueOf(row.getItemId())); contest.add(team);
        }
        int ordinal = 0;
        for (BoardVO.ProblemInfo problem : finalBoard.getProblems()) {
            Problem p = new Problem(); p.add("id", problem.getId()); p.add("label", problem.getId());
            p.add("name", problem.getName() == null ? problem.getId() : problem.getName());
            p.add("ordinal", ordinal++); contest.add(p);
        }
        addJudgementType(contest, AC, true, false);
        addJudgementType(contest, WRONG, false, true);
        addJudgementType(contest, COMPILE, false, false);
        for (Map<String, Object> record : submissions) {
            String id = String.valueOf(record.get("submissionId"));
            Submission sub = new Submission();
            sub.add("id", id); sub.add("team_id", String.valueOf(record.get("participantId")));
            sub.add("problem_id", String.valueOf(record.get("problemId")));
            long time = ((Number) record.get("timeMillis")).longValue();
            long engineTime = Math.multiplyExact(time, TIME_SCALE);
            // Timestamps can differ from the freeze by microseconds but round to the same
            // scoring millisecond; use the authoritative frozen flag without changing rank.
            if (Boolean.TRUE.equals(record.get("frozen")) && time == freezeAt) engineTime = Math.addExact(engineTime, 1);
            sub.add("contest_time", RelativeTime.format(engineTime));
            contest.add(sub);
            String verdict = (String) record.get("result");
            String type = verdict != null && (verdict.equalsIgnoreCase("Accepted") || verdict.equalsIgnoreCase("AC") || verdict.equalsIgnoreCase("Correct")) ? AC
                    : "Compile Error".equals(verdict) || "CE".equals(verdict) ? COMPILE : WRONG;
            contest.add(new Judgement(id, sub, type));
        }
        var awards = settings.getAwards();
        if (Boolean.TRUE.equals(awards.getShowMedals())) {
            for (String medal : List.of("Gold", "Silver", "Bronze")) {
                String[] winners = finalBoard.getRows().stream().filter(row -> medal.equals(row.getMedal()))
                        .map(row -> String.valueOf(row.getItemId())).toArray(String[]::new);
                String label = switch (medal) { case "Gold" -> awards.getGoldLabel(); case "Silver" -> awards.getSilverLabel(); default -> awards.getBronzeLabel(); };
                if (winners.length > 0) {
                    contest.add(new Award(IAward.MEDAL, medal.toLowerCase(Locale.ROOT), winners, label, IAward.DisplayMode.DETAIL));
                    // A separate list award retains individual photos and follows Resolver's
                    // own group-list scheduling and screens for each medal tier.
                    Award summary = new Award(IAward.OTHER, "medal-summary-" + medal.toLowerCase(Locale.ROOT), winners, label, IAward.DisplayMode.LIST);
                    // ResolverLogic schedules an "after" list immediately after the
                    // highest-ranked member of the group has received their award.
                    summary.getParameters().put("after", "true");
                    contest.add(summary);
                }
            }
        }
        if (Boolean.TRUE.equals(awards.getShowPodium())) {
            for (int place = 1; place <= 3; place++) {
                int rank = place;
                String[] winners = finalBoard.getRows().stream()
                        .filter(row -> !Boolean.TRUE.equals(row.getIsStar()) && Objects.equals(row.getRank(), rank))
                        .map(row -> String.valueOf(row.getItemId())).toArray(String[]::new);
                String label = switch (place) { case 1 -> awards.getChampionLabel(); case 2 -> awards.getRunnerUpLabel(); default -> awards.getSecondRunnerUpLabel(); };
                if (winners.length > 0) contest.add(new Award(IAward.OTHER, "podium-" + place, winners, label, IAward.DisplayMode.DETAIL));
            }
        }
        contest.setConfigurationLoaded();
        if (Boolean.TRUE.equals(awards.getFirstToSolve())) AwardUtil.createFirstToSolveAwards(contest, true, true);
        if (Boolean.TRUE.equals(awards.getSolvedCount())) AwardUtil.createSolutionAwards(contest);
        if (Boolean.TRUE.equals(awards.getBestFemaleTeam())) {
            List<RankItemVO> femaleParticipants = finalBoard.getRows().stream()
                    .filter(row -> Boolean.TRUE.equals(row.getIsGirl()) && !Boolean.TRUE.equals(row.getIsStar()) && row.getRank() != null && row.getRank() > 0)
                    .toList();
            int bestRank = femaleParticipants.stream().mapToInt(RankItemVO::getRank).min().orElse(Integer.MAX_VALUE);
            String[] winners = femaleParticipants.stream().filter(row -> row.getRank() == bestRank)
                    .map(row -> String.valueOf(row.getItemId())).toArray(String[]::new);
            if (winners.length > 0) contest.add(new Award(IAward.OTHER, "best-female-team", winners, awards.getBestFemaleLabel(), IAward.DisplayMode.DETAIL));
        }
        int awardIndex = 0;
        for (ResolverSettingsDTO.CustomAward custom : awards.getCustomAwards()) {
            for (Long id : custom.getParticipantIds()) if (contest.getTeamById(String.valueOf(id)) == null)
                throw new IllegalArgumentException("自定义奖项包含不属于当前外榜的参赛者");
            IAward.DisplayMode mode = switch (custom.getDisplayMode()) { case "list" -> IAward.DisplayMode.LIST; case "photos" -> IAward.DisplayMode.PHOTOS; default -> IAward.DisplayMode.DETAIL; };
            contest.add(new Award(IAward.OTHER, "custom-" + awardIndex++, custom.getParticipantIds().stream().map(String::valueOf).toArray(String[]::new), custom.getCitation(), mode));
        }
        return contest;
    }

    private static Map<String,Object> event(String type) { Map<String,Object> data = new LinkedHashMap<>(); data.put("type", type); return data; }
    private static Map<String,Object> submissionEvent(String type, Map<String,Object> submission) {
        Map<String,Object> data = event(type);
        for (String field : List.of("submissionId", "participantId", "problemId")) data.put(field, submission == null ? null : submission.get(field));
        return data;
    }
    private static String medalName(IAward award) {
        if (award.getAwardType() != IAward.MEDAL) return null;
        for (String name : List.of("Gold", "Silver", "Bronze"))
            if (award.getId().toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) return name;
        return null;
    }
    private static String summaryMedalName(IAward award) {
        String id = award.getId();
        for (String name : List.of("Gold", "Silver", "Bronze"))
            if (("medal-summary-" + name.toLowerCase(Locale.ROOT)).equals(id)) return name;
        return null;
    }
    private static List<Map<String, String>> combineFirstSolves(List<IAward> awards, Contest contest) {
        Set<String> awardIds = new HashSet<>();
        for (IAward award : awards) if (award.getAwardType() == IAward.FIRST_TO_SOLVE) awardIds.add(award.getId());
        List<String> problems = Arrays.stream(contest.getProblems())
                .filter(p -> awardIds.contains(IAward.FIRST_TO_SOLVE.getPattern(p.getId())))
                .map(p -> p.getLabel() == null ? p.getId() : p.getLabel()).toList();
        String combined = null;
        if (!problems.isEmpty()) {
            if (problems.size() == 1) combined = problems.get(0);
            else combined = String.join(", ", problems.subList(0, problems.size() - 1)) + " and " + problems.get(problems.size() - 1);
            combined = "First Solve " + combined;
        }
        List<Map<String, String>> result = new ArrayList<>();
        boolean addedFirstSolves = false;
        for (IAward award : awards) {
            if (award.getAwardType() == IAward.FIRST_TO_SOLVE && combined != null) {
                if (!addedFirstSolves) result.add(Map.of("citation", combined, "type", IAward.FIRST_TO_SOLVE.getName()));
                addedFirstSolves = true;
            } else if (award.getCitation() != null) {
                String type = switch (award.getId()) {
                    case "best-female-team" -> "Best Female Team";
                    case "podium-1" -> "Champion";
                    case "podium-2" -> "Runner-up";
                    case "podium-3" -> "Second Runner-up";
                    default -> award.getAwardType() == null ? "Other" : award.getAwardType().getName();
                };
                result.add(Map.of("citation", award.getCitation(), "type", type));
            }
        }
        return result;
    }
    private static void addJudgementType(Contest contest, String id, boolean solved, boolean penalty) {
        JudgementType type = new JudgementType(); type.add("id", id); type.add("name", id); type.add("solved", solved); type.add("penalty", penalty); contest.add(type);
    }
}
