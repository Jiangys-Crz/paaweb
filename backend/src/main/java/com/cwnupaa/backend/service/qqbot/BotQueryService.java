package com.cwnupaa.backend.service.qqbot;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Strict, non-executable command grammar. Only public website data and two Hydro read APIs are exposed. */
@Service
public class BotQueryService {
    private static final String WEBSITE = com.cwnupaa.backend.utils.DeploymentUrls.site();
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Pattern USER_COMMAND = Pattern.compile("^/(rating|tracker) ([A-Za-z0-9_][A-Za-z0-9_.-]{0,63})$", Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> COMMANDS = Map.ofEntries(
            Map.entry("offline contest", "offline"), Map.entry("近期线下赛", "offline"),
            Map.entry("online contest", "online"), Map.entry("近期线上赛", "online"),
            Map.entry("training", "training"), Map.entry("培训", "training"),
            Map.entry("notice", "notice"), Map.entry("通知", "notice"),
            Map.entry("news", "news"), Map.entry("新闻", "news"),
            Map.entry("publicity", "publicity"), Map.entry("公示", "publicity"),
            Map.entry("board", "board"), Map.entry("外榜", "board"),
            Map.entry("help", "help"), Map.entry("帮助", "help"));
    private static final String HELP = "协会机器人指令（时间均为北京时间）\n"
            + "offline contest / 近期线下赛：查询未结束的官网赛事\n"
            + "online contest / 近期线上赛：查询协会 OJ 和 CP Tracker 近期比赛\n"
            + "training / 培训：查询未结束的培训\n"
            + "notice / 通知：最近 5 条通知\n"
            + "news / 新闻：最近 5 条新闻\n"
            + "publicity / 公示：最近 5 条公示\n"
            + "board / 外榜：最近 5 条公开外榜\n"
            + "/rating 官网用户名：查询协会 OJ Rating 资料卡\n"
            + "/tracker 官网用户名：查询五个平台解题资料卡\n"
            + "help / 帮助：显示此帮助\n"
            + "用户名请填写官网账号，每次仅发送一条完整指令。";

    private final EventMapper events;
    private final TrainingMapper trainings;
    private final TrainingTeacherMapper teachers;
    private final NoticeMapper notices;
    private final ExternalBoardMapper boards;
    private final OjRecentContestMapper recent;
    private final OjPlatformMapper platforms;
    private final UserMapper users;
    private final OjPracticeRecordMapper practice;
    private final HydroBotClient hydro;
    private final BotCardRenderer renderer;

    public BotQueryService(EventMapper events, TrainingMapper trainings, TrainingTeacherMapper teachers,
                           NoticeMapper notices, ExternalBoardMapper boards, OjRecentContestMapper recent,
                           OjPlatformMapper platforms, UserMapper users, OjPracticeRecordMapper practice,
                           HydroBotClient hydro, BotCardRenderer renderer) {
        this.events = events; this.trainings = trainings; this.teachers = teachers; this.notices = notices;
        this.boards = boards; this.recent = recent; this.platforms = platforms; this.users = users;
        this.practice = practice; this.hydro = hydro; this.renderer = renderer;
    }

    /** Used only to decide whether an ordinary unmentioned chat message should be ignored. */
    public boolean recognized(String command) {
        if (command == null) return false;
        String normalized = command.strip().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/")) return true;
        return COMMANDS.keySet().stream().anyMatch(c -> normalized.equals(c) || (normalized.startsWith(c)
                && normalized.length() > c.length() && Character.isWhitespace(normalized.charAt(c.length()))));
    }

    public BotReply reply(String command) {
        if (command == null || command.length() > 256) return text(HELP);
        String normalized = command.strip();
        // No multiline payloads, CQ escapes, JSON commands, prompt interpretation or fuzzy username lookup.
        if (normalized.indexOf('\n') >= 0 || normalized.indexOf('\r') >= 0 || normalized.indexOf('[') >= 0) return text(HELP);
        String key = COMMANDS.get(normalized.toLowerCase(Locale.ROOT));
        LocalDateTime now = LocalDateTime.now(ZONE);
        if (key != null) {
            return switch (key) {
                case "offline" -> text(join("近期线下赛", upcomingEvents(now).stream().map(this::eventText).toList()));
                case "training" -> text(join("近期培训", upcomingTrainings(now).stream().map(this::trainingText).toList()));
                case "online" -> text(online(now));
                case "notice", "news", "publicity" -> text(notices(key, now));
                case "board" -> text(join("最近公开外榜", publicBoards().stream().limit(5).map(this::boardText).toList()));
                default -> text(HELP);
            };
        }
        Matcher match = USER_COMMAND.matcher(normalized);
        if (!match.matches()) return text(HELP);
        String account = match.group(2);
        // An exact equality with a bound parameter cannot become an SQL selector or privileged operation.
        Users user = users.selectOne(new QueryWrapper<Users>().eq("user_account", account).last("LIMIT 1"));
        if (user == null || !account.equals(user.getUserAccount())) return text("未找到该官网账号。请使用 /" + match.group(1).toLowerCase(Locale.ROOT) + " 官网用户名，用户名须完全一致。");
        if ("rating".equalsIgnoreCase(match.group(1))) {
            try { return new BotReply("协会 OJ Rating · " + safe(user.getUserAccount()), renderer.rating(user, hydro.profile(user.getEmail()))); }
            catch (HydroBotClient.HydroUnavailableException e) { return text(e.getMessage()); }
        }
        Map<Long, Set<String>> solved = new HashMap<>();
        for (OjPracticeRecord row : practice.selectList(new QueryWrapper<OjPracticeRecord>().eq("user_id", user.getUserId()))) {
            if (row.getSourceId() == null || row.getSourceId() < 1 || row.getSourceId() > 5 || row.getProblemId() == null) continue;
            solved.computeIfAbsent(row.getSourceId(), ignored -> new HashSet<>()).add(row.getProblemId());
        }
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (long source = 1; source <= 5; source++) counts.put(source, (long) solved.getOrDefault(source, Set.of()).size());
        return new BotReply("CP Tracker · " + safe(user.getUserAccount()), renderer.tracker(user, counts));
    }

    /** Stable per-event keys allow the delivery layer to persist deduplication separately for every group. */
    public List<BotPush> pushes(LocalDateTime now) {
        List<BotPush> result = new ArrayList<>();
        for (Event event : upcomingEvents(now)) {
            if (event.getSignupStart() != null) result.add(new BotPush("offline:" + event.getEventId() + ":" + event.getSignupStart(),
                    "offline", event.getSignupStart().minusDays(1), "线下赛报名提醒（明日开始报名）\n" + eventText(event)));
        }
        for (Training training : upcomingTrainings(now)) {
            if (training.getSignupStart() != null) result.add(new BotPush("training:" + training.getTrainId() + ":" + training.getSignupStart(),
                    "training", training.getSignupStart().minusDays(1), "培训报名提醒（明日开始报名）\n" + trainingText(training)));
        }
        for (ExternalBoard board : publicBoards()) {
            if (board.getActualStartTime() != null && board.getActualStartTime().isAfter(now)) {
                result.add(new BotPush("board:" + board.getBoardId() + ":" + board.getActualStartTime(), "board",
                        board.getActualStartTime().minusMinutes(10), "外榜提醒（比赛即将开始）\n" + boardText(board)));
            }
        }
        // Only the Hydro system-domain API participates in pushes. Third-party contests never do.
        try {
            for (HydroBotClient.HydroContest contest : hydro.contests()) {
                if (!contest.beginAt().isAfter(now)) continue;
                for (int hours : List.of(24, 3)) {
                    result.add(new BotPush("online:" + contest.id() + ":" + contest.beginAt() + ":" + hours, "online",
                            contest.beginAt().minusHours(hours), "协会 OJ 比赛提醒（距开始 " + hours + " 小时）\n" + hydroText(contest)));
                }
            }
        } catch (HydroBotClient.HydroUnavailableException ignored) { /* Local notifications remain available during Hydro outages. */ }
        return result;
    }

    private List<Event> upcomingEvents(LocalDateTime now) {
        return events.selectList(new QueryWrapper<Event>().eq("status", 1).ge("event_end", now)
                .orderByAsc("event_start").last("LIMIT 100")).stream()
                .filter(e -> Integer.valueOf(1).equals(e.getStatus()) && e.getEventEnd() != null && !e.getEventEnd().isBefore(now))
                .sorted(Comparator.comparing(Event::getEventStart, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
    }

    private List<Training> upcomingTrainings(LocalDateTime now) {
        // Training has no draft state in the website: every saved training is publicly listed.
        return trainings.selectList(new QueryWrapper<Training>().ge("train_end", now).orderByAsc("train_start").last("LIMIT 100")).stream()
                .filter(t -> t.getTrainEnd() != null && !t.getTrainEnd().isBefore(now))
                .sorted(Comparator.comparing(Training::getTrainStart, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
    }

    private List<ExternalBoard> publicBoards() {
        return boards.selectList(new QueryWrapper<ExternalBoard>().eq("is_external_open", true).orderByDesc("board_id").last("LIMIT 100"))
                .stream().filter(b -> Boolean.TRUE.equals(b.getExternalOpen())).sorted(Comparator.comparing(ExternalBoard::getBoardId).reversed()).toList();
    }

    private String notices(String type, LocalDateTime now) {
        String title = switch (type) { case "news" -> "新闻"; case "publicity" -> "公示"; default -> "通知"; };
        List<Notice> rows = notices.selectList(new QueryWrapper<Notice>().eq("type", type).le("create_time", now)
                .orderByDesc("create_time", "info_id").last("LIMIT 5"));
        return join("最近" + title, rows.stream().filter(n -> type.equals(n.getType()) && n.getCreateTime() != null && !n.getCreateTime().isAfter(now))
                .limit(5).map(n -> safe(n.getTitle()) + "\n发布时间：" + at(n.getCreateTime()) + "\n发布者：" + safe(n.getPublisherName())
                        + "\n链接：" + WEBSITE + "/" + type + "/" + n.getInfoId()).toList());
    }

    private String eventText(Event event) {
        return safe(event.getName()) + "\n比赛类型：" + safe(event.getType()) + (Integer.valueOf(1).equals(event.getIsTeam()) ? "（团队赛）" : "（个人赛）")
                + "\n报名时间：" + range(event.getSignupStart(), event.getSignupEnd()) + "\n比赛时间：" + range(event.getEventStart(), event.getEventEnd())
                + "\n报名上限：" + limit(event.getMaxSignup(), Integer.valueOf(1).equals(event.getIsTeam()) ? "队" : "人")
                + "\n比赛链接：" + WEBSITE + "/contest/signup/" + event.getEventId();
    }

    private String trainingText(Training training) {
        List<Long> ids = new ArrayList<>(teachers.selectList(new QueryWrapper<TrainingTeacher>().eq("train_id", training.getTrainId()).orderByAsc("sort_order"))
                .stream().map(TrainingTeacher::getTeacherId).filter(Objects::nonNull).distinct().toList());
        if (ids.isEmpty() && training.getTeacherId() != null) ids.add(training.getTeacherId());
        String names = ids.stream().map(users::selectById).filter(Objects::nonNull)
                .map(u -> safe(u.getRealName() == null || u.getRealName().isBlank() ? u.getUserName() : u.getRealName())).collect(Collectors.joining("、"));
        return safe(training.getName()) + "\n主讲人：" + (names.isEmpty() ? "待公布" : names)
                + "\n报名时间：" + range(training.getSignupStart(), training.getSignupEnd())
                + "\n培训时间：" + range(training.getTrainStart(), training.getTrainEnd()) + "\n报名上限：" + limit(training.getMaxCount(), "人")
                + "\n报名链接：" + WEBSITE + "/training/" + training.getTrainId();
    }

    private String boardText(ExternalBoard board) {
        return safe(board.getName()) + "\n比赛时间：" + range(board.getActualStartTime(), board.getActualEndTime())
                + "\n外榜链接：" + WEBSITE + "/contest/board/" + board.getBoardId();
    }

    private String online(LocalDateTime now) {
        List<TimedText> entries = new ArrayList<>();
        String unavailable = "";
        try {
            hydro.contests().stream().filter(c -> !c.endAt().isBefore(now))
                    .forEach(c -> entries.add(new TimedText(c.beginAt(), hydroText(c))));
        } catch (HydroBotClient.HydroUnavailableException e) { unavailable = "\n" + e.getMessage(); }
        Map<Long, String> names = new HashMap<>();
        for (OjPlatform p : platforms.selectList(null)) names.put(p.getOjId(), p.getOjName());
        for (OjRecentContest contest : recent.selectList(new QueryWrapper<OjRecentContest>().and(q -> q.ge("contests_starttime", now)
                .or().ge("contests_endtime", now)).orderByAsc("contests_starttime").last("LIMIT 30"))) {
            String platform = platform(names.get(contest.getSourceId()));
            if (platform == null || contest.getContestsStarttime() == null || (contest.getContestsStarttime().isBefore(now)
                    && (contest.getContestsEndtime() == null || contest.getContestsEndtime().isBefore(now)))) continue;
            if (contest.getContestsStarttime().isAfter(now) && ((contest.getRegistrationStart() != null && contest.getRegistrationStart().isAfter(now))
                    || (contest.getRegistrationEnd() != null && contest.getRegistrationEnd().isBefore(now)))) continue;
            String link = publicContestUrl(contest.getUrl(), platform);
            entries.add(new TimedText(contest.getContestsStarttime(), "平台：" + platform + "\n" + safe(contest.getContestsName())
                    + "\n开始时间：" + at(contest.getContestsStarttime()) + "\n结束时间：" + (contest.getContestsEndtime() == null ? "平台未提供，请查看报名链接" : at(contest.getContestsEndtime()))
                    + "\n报名时间：" + (contest.getRegistrationStart() == null && contest.getRegistrationEnd() == null ? "平台未提供，请查看报名链接"
                    : range(contest.getRegistrationStart(), contest.getRegistrationEnd())) + "\n报名链接：" + link));
        }
        return join("近期线上赛", entries.stream().sorted(Comparator.comparing(TimedText::time)).limit(30).map(TimedText::text).toList()) + unavailable;
    }

    private String hydroText(HydroBotClient.HydroContest contest) {
        return "平台：协会 OJ\n" + safe(contest.title()) + "\n开始时间：" + at(contest.beginAt()) + "\n结束时间：" + at(contest.endAt())
                + "\n报名时间：" + (contest.registrationStart() == null ? "开放报名" : at(contest.registrationStart())) + " 至 " + at(contest.registrationEnd())
                + "\n报名链接：" + contest.url();
    }

    private static String platform(String source) {
        if (source == null) return null;
        return switch (source.toLowerCase(Locale.ROOT).replace(" ", "")) {
            case "codeforces", "cf" -> "CodeForces";
            case "atcoder" -> "AtCoder";
            case "nowcoder", "牛客" -> "牛客";
            default -> null;
        };
    }

    private static String publicContestUrl(String value, String platform) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            String allowed = switch (platform) { case "CodeForces" -> "codeforces.com"; case "AtCoder" -> "atcoder.jp"; default -> "nowcoder.com"; };
            if ("https".equals(uri.getScheme()) && uri.getUserInfo() == null && host != null && (host.equals(allowed) || host.endsWith("." + allowed))) return value;
        } catch (Exception ignored) { }
        return switch (platform) { case "CodeForces" -> "https://codeforces.com/contests"; case "AtCoder" -> "https://atcoder.jp/contests/"; default -> "https://ac.nowcoder.com/acm/contest/vip-index"; };
    }

    private static String join(String title, List<String> entries) { return title + "（北京时间）\n\n" + (entries.isEmpty() ? "暂无信息。" : String.join("\n\n", entries)); }
    private static String range(LocalDateTime start, LocalDateTime end) { return at(start) + " 至 " + at(end); }
    private static String at(LocalDateTime time) { return time == null ? "待公布" : time.format(TIME); }
    private static String limit(Integer number, String unit) { return number == null || number <= 0 ? "不限" : number + unit; }
    static String safe(String value) {
        if (value == null || value.isBlank()) return "待公布";
        String text = value.replaceAll("[\\p{Cntrl}\\u202A-\\u202E\\u2066-\\u2069]", " ").replace('[', '【').replace(']', '】');
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }
    private static BotReply text(String value) { return new BotReply(value, null); }
    private record TimedText(LocalDateTime time, String text) {}
    public record BotReply(String text, byte[] image) {}
    public record BotPush(String key, String category, LocalDateTime dueAt, String text) {}
}
