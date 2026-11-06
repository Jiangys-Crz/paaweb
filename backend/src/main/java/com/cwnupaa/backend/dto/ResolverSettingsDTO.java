package com.cwnupaa.backend.dto;

import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Per-board ceremony awards and display, with bounded user-supplied data. */
@Data
public class ResolverSettingsDTO {
    private Awards awards = new Awards();
    private Display display = new Display();

    @Data public static class Awards {
        private Boolean showMedals = true;
        private Boolean showPodium = true;
        private String championLabel = "冠军 Champion";
        private String runnerUpLabel = "亚军 Runner-up";
        private String secondRunnerUpLabel = "季军 Second Runner-up";
        private Boolean firstToSolve = false;
        private Boolean bestFemaleTeam = false;
        private String bestFemaleLabel = "最佳女队 Best Female";
        private Boolean solvedCount = false;
        private String goldLabel = "Gold Medal";
        private String silverLabel = "Silver Medal";
        private String bronzeLabel = "Bronze Medal";
        private List<CustomAward> customAwards = new ArrayList<>();

        public String getBestFemaleLabel() {
            if (bestFemaleLabel == null) return null;
            return bestFemaleLabel.trim().matches("(?i)(?:best\\s+female(?:\\s+team)?|最佳女队(?:奖)?(?:\\s+best\\s+female(?:\\s+team)?)?)")
                    ? "最佳女队 Best Female" : bestFemaleLabel;
        }
    }
    @Data public static class CustomAward {
        private String citation;
        private List<Long> participantIds = new ArrayList<>();
        private String displayMode = "detail";
    }
    @Data public static class Display {
        private Integer rowsPerScreen = 12;
        private String nameTemplate = "{org.name}: {team.name}";
        private String theme = "dark";
        private Boolean showOrganizationLogo = true;
        private Boolean showTeamPhoto = true;
        private Boolean showOrganization = true;
        private Boolean showMembers = true;
    }
    private static ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    public void validate() {
        if (awards == null || display == null || awards.showMedals == null || awards.showPodium == null || awards.firstToSolve == null || awards.bestFemaleTeam == null || awards.solvedCount == null)
            throw invalid("滚榜奖项设置不完整");
        for (String label : List.of(nonNull(awards.goldLabel), nonNull(awards.silverLabel), nonNull(awards.bronzeLabel), nonNull(awards.bestFemaleLabel),
                nonNull(awards.championLabel), nonNull(awards.runnerUpLabel), nonNull(awards.secondRunnerUpLabel)))
            if (label.isBlank() || label.length() > 100) throw invalid("奖项名称须为 1 至 100 个字符");
        if (awards.customAwards == null || awards.customAwards.size() > 20) throw invalid("自定义奖项最多 20 项");
        for (CustomAward award : awards.customAwards) {
            if (award == null || award.citation == null || award.citation.isBlank() || award.citation.length() > 100)
                throw invalid("自定义奖项名称须为 1 至 100 个字符");
            if (award.displayMode == null || !Set.of("detail", "list", "photos").contains(award.displayMode)) throw invalid("自定义奖项展示方式无效");
            if (award.participantIds == null || award.participantIds.isEmpty() || award.participantIds.size() > 500
                    || award.participantIds.stream().anyMatch(id -> id == null || id <= 0)
                    || new HashSet<>(award.participantIds).size() != award.participantIds.size())
                throw invalid("每个自定义奖项须指定 1 至 500 个不重复参赛者");
        }
        if (display.rowsPerScreen == null || display.rowsPerScreen < 6 || display.rowsPerScreen > 30)
            throw invalid("每屏队伍数须为 6 至 30");
        if (display.theme == null || !Set.of("dark", "light").contains(display.theme) || display.showOrganizationLogo == null || display.showTeamPhoto == null
                || display.showOrganization == null || display.showMembers == null) throw invalid("滚榜展示设置无效");
        validateNameTemplate(display.nameTemplate);
    }
    private static void validateNameTemplate(String value) {
        if (value == null || value.isBlank() || value.length() > 200) throw invalid("显示名称模板须为 1 至 200 个字符");
        Set<String> tokens = Set.of("org.name", "org.formal_name", "team.name", "team.display_name", "team.english_name");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '}') throw invalid("显示名称模板包含无效占位符");
            if (ch != '{') continue;
            int end = value.indexOf('}', i + 1);
            if (end < 0 || !tokens.contains(value.substring(i + 1, end))) throw invalid("显示名称模板包含无效占位符");
            i = end;
        }
    }
    private static String nonNull(String value) { return value == null ? "" : value; }
}
