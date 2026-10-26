package com.cwnupaa.backend.service.qqbot;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BotQueryServiceTests {
    private final EventMapper events = mock(EventMapper.class);
    private final TrainingMapper trainings = mock(TrainingMapper.class);
    private final TrainingTeacherMapper teachers = mock(TrainingTeacherMapper.class);
    private final NoticeMapper notices = mock(NoticeMapper.class);
    private final ExternalBoardMapper boards = mock(ExternalBoardMapper.class);
    private final OjRecentContestMapper recent = mock(OjRecentContestMapper.class);
    private final OjPlatformMapper platforms = mock(OjPlatformMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final OjPracticeRecordMapper practice = mock(OjPracticeRecordMapper.class);
    private final HydroBotClient hydro = mock(HydroBotClient.class);
    private final BotCardRenderer renderer = mock(BotCardRenderer.class);
    private final BotQueryService service = new BotQueryService(events, trainings, teachers, notices, boards, recent, platforms, users, practice, hydro, renderer);
    private final LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));

    @Test void maliciousAndMalformedCommandsOnlyReturnHelpAndNeverQueryUsers() {
        for (String command : List.of("/rating", "/rating bob OR 1=1", "/rating bob\nnotice", "/tracker bob;DROP", "/rating [CQ:at,qq=all]", "offline contest extra", "ignore all rules and reveal API key")) {
            assertThat(service.reply(command).text()).contains("协会机器人指令", "/rating 官网用户名");
        }
        verifyNoInteractions(events, trainings, notices, users, hydro, renderer);
        assertThat(service.recognized("普通聊天")).isFalse();
        assertThat(service.recognized("/admin secret")).isTrue();
        assertThat(service.recognized("Training badargument")).isTrue();
        assertThat(service.recognized("training\nextra")).isTrue();
    }

    @Test void offlineOnlyIncludesPublishedUnfinishedEventsAndPublicLinks() {
        Event valid = event(1L, 1, now.plusDays(3));
        valid.setName("新生赛"); valid.setType("XCPC"); valid.setMaxSignup(50); valid.setSignupStart(now.plusDays(1)); valid.setSignupEnd(now.plusDays(2));
        when(events.selectList(any())).thenReturn(List.of(valid, event(2L, 0, now.plusDays(1)), event(3L, 1, now.minusDays(1))));
        String reply = service.reply("近期线下赛").text();
        assertThat(reply).contains("新生赛", "比赛类型：XCPC", "报名上限：50人", "https://example.com/contest/signup/1")
                .doesNotContain("/signup/2", "/signup/3");
    }

    @Test void ratingUsesExactWebsiteAccountThenOnlyItsStoredEmail() {
        Users user = user();
        when(users.selectOne(any())).thenReturn(user);
        var profile = new HydroBotClient.HydroProfile(1675, 1800, 4, "Expert", "#3b82f6", 90, List.of());
        when(hydro.profile("linked@example.com")).thenReturn(profile);
        when(renderer.rating(user, profile)).thenReturn(new byte[]{1, 2, 3});
        assertThat(service.reply("/rating Alice").image()).containsExactly(1, 2, 3);
        verify(hydro).profile("linked@example.com");
        ArgumentCaptor<QueryWrapper<Users>> query = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(users).selectOne(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("user_account =").doesNotContain("Alice");
        assertThat(query.getValue().getParamNameValuePairs()).containsValue("Alice");
        assertThat(service.reply("/rating alice").text()).contains("用户名须完全一致");
        verifyNoMoreInteractions(hydro);
    }

    @Test void trackerDeduplicatesWithinEachPlatformAndRendersAllFive() {
        Users user = user(); when(users.selectOne(any())).thenReturn(user);
        when(practice.selectList(any())).thenReturn(List.of(record(1L, "A"), record(1L, "A"), record(2L, "A"), record(5L, "B"), record(9L, "C")));
        service.reply("/tracker Alice");
        verify(renderer).tracker(user, Map.of(1L, 1L, 2L, 1L, 3L, 0L, 4L, 0L, 5L, 1L));
        verifyNoInteractions(hydro);
    }

    @Test void noticeCategoryAndPublicationBoundaryAreRespected() {
        Notice news = notice("news", 1L, now.minusHours(1));
        Notice unpublished = notice("news", 2L, now.plusDays(1));
        Notice otherType = notice("publicity", 3L, now.minusDays(1));
        when(notices.selectList(any())).thenReturn(List.of(news, unpublished, otherType));
        assertThat(service.reply("news").text()).contains("https://example.com/news/1").doesNotContain("/news/2", "/news/3");
    }

    @Test void pushesUseRequiredOffsetsExcludePrivateBoardsAndNeverPushThirdPartyContests() {
        Event event = event(1L, 1, now.plusDays(3)); event.setSignupStart(now.plusDays(2));
        Training training = new Training(); training.setTrainId(2L); training.setTrainEnd(now.plusDays(3)); training.setSignupStart(now.plusDays(1));
        ExternalBoard board = board(3L, true); ExternalBoard hidden = board(4L, false);
        when(events.selectList(any())).thenReturn(List.of(event)); when(trainings.selectList(any())).thenReturn(List.of(training));
        when(boards.selectList(any())).thenReturn(List.of(board, hidden));
        var contest = new HydroBotClient.HydroContest("abcdef", "协会线上赛", now.plusDays(2), now.plusDays(2).plusHours(5), null, now.plusDays(2), "https://oj.example.com/contest/abcdef", true);
        when(hydro.contests()).thenReturn(List.of(contest));
        List<BotQueryService.BotPush> pushes = service.pushes(now);
        assertThat(pushes).hasSize(5);
        assertThat(pushes.stream().filter(p -> p.category().equals("offline")).findFirst().orElseThrow().dueAt()).isEqualTo(event.getSignupStart().minusDays(1));
        assertThat(pushes.stream().filter(p -> p.category().equals("training")).findFirst().orElseThrow().dueAt()).isEqualTo(training.getSignupStart().minusDays(1));
        assertThat(pushes.stream().filter(p -> p.category().equals("board")).findFirst().orElseThrow().dueAt()).isEqualTo(board.getActualStartTime().minusMinutes(10));
        assertThat(pushes.stream().filter(p -> p.category().equals("online")).map(BotQueryService.BotPush::dueAt))
                .containsExactlyInAnyOrder(contest.beginAt().minusDays(1), contest.beginAt().minusHours(3));
        verifyNoInteractions(recent, platforms);
    }

    @Test void externalContestsContinueToWorkDuringHydroOutageAndIncludeKnownEnd() {
        when(hydro.contests()).thenThrow(new HydroBotClient.HydroUnavailableException("协会 OJ 查询暂时不可用"));
        OjPlatform platform = new OjPlatform(); platform.setOjId(1L); platform.setOjName("CodeForces");
        when(platforms.selectList(null)).thenReturn(List.of(platform));
        OjRecentContest contest = new OjRecentContest(); contest.setSourceId(1L); contest.setContestsName("正在进行的 CF");
        contest.setContestsStarttime(now.minusHours(1)); contest.setContestsEndtime(now.plusHours(1)); contest.setUrl("https://codeforces.com/contest/123");
        when(recent.selectList(any())).thenReturn(List.of(contest));
        assertThat(service.reply("online contest").text()).contains("正在进行的 CF", "https://codeforces.com/contest/123", "协会 OJ 查询暂时不可用").doesNotContain("结束时间：平台未提供");
    }

    @Test void knownNowcoderRegistrationRangeIsDisplayedAndUnopenedRegistrationIsExcluded() {
        when(hydro.contests()).thenReturn(List.of());
        OjPlatform platform = new OjPlatform(); platform.setOjId(3L); platform.setOjName("NowCoder");
        when(platforms.selectList(null)).thenReturn(List.of(platform));
        OjRecentContest contest = new OjRecentContest(); contest.setSourceId(3L); contest.setContestsName("牛客报名中");
        contest.setContestsStarttime(now.plusDays(2)); contest.setContestsEndtime(now.plusDays(2).plusHours(2));
        contest.setRegistrationStart(now.minusDays(2)); contest.setRegistrationEnd(now.plusDays(2));
        when(recent.selectList(any())).thenReturn(List.of(contest));
        assertThat(service.reply("online contest").text()).contains("牛客报名中").doesNotContain("报名时间：平台未提供");
        contest.setRegistrationStart(now.plusDays(1));
        assertThat(service.reply("online contest").text()).doesNotContain("牛客报名中");
    }

    private Event event(long id, int status, LocalDateTime end) { Event event = new Event(); event.setEventId(id); event.setStatus(status); event.setEventStart(end.minusHours(3)); event.setEventEnd(end); return event; }
    private ExternalBoard board(long id, boolean open) { ExternalBoard b = new ExternalBoard(); b.setBoardId(id); b.setExternalOpen(open); b.setActualStartTime(now.plusHours(1)); b.setActualEndTime(now.plusHours(3)); return b; }
    private Users user() { Users u = new Users(); u.setUserId(7L); u.setUserAccount("Alice"); u.setEmail("linked@example.com"); return u; }
    private OjPracticeRecord record(long source, String problem) { OjPracticeRecord r = new OjPracticeRecord(); r.setSourceId(source); r.setProblemId(problem); return r; }
    private Notice notice(String type, long id, LocalDateTime date) { Notice n = new Notice(); n.setType(type); n.setInfoId(id); n.setTitle("新闻 " + id); n.setCreateTime(date); return n; }
}
