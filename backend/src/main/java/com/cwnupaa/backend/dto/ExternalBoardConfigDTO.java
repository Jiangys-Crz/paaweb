package com.cwnupaa.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public class ExternalBoardConfigDTO {
    private Long boardId;
    private String configSection;
    public String getConfigSection() { return configSection; }
    public void setConfigSection(String value) { configSection = value; }
    private String organizationDisplay;
    public String getOrganizationDisplay() { return organizationDisplay; }
    public void setOrganizationDisplay(String value) { organizationDisplay = value; }
    private Boolean resolverSingleStep;
    private Double resolverSpeed;
    private ResolverSettingsDTO resolverSettings;
    public ResolverSettingsDTO getResolverSettings() { return resolverSettings; }
    public void setResolverSettings(ResolverSettingsDTO value) { resolverSettings = value; }
    public Boolean getResolverSingleStep() { return resolverSingleStep; }
    public void setResolverSingleStep(Boolean value) { resolverSingleStep = value; }
    public Double getResolverSpeed() { return resolverSpeed; }
    public void setResolverSpeed(Double value) { resolverSpeed = value; }
    private Boolean resolverEnabled;
    private Boolean resolverPasswordSet;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private String resolverPassword;

    public Boolean getResolverEnabled() { return resolverEnabled; }
    public void setResolverEnabled(Boolean value) { resolverEnabled = value; }
    public Boolean getResolverPasswordSet() { return resolverPasswordSet; }
    public void setResolverPasswordSet(Boolean value) { resolverPasswordSet = value; }
    public String getResolverPassword() { return resolverPassword; }
    public void setResolverPassword(String value) { resolverPassword = value; }


    public Long getBoardId() { return boardId; }
    public void setBoardId(Long boardId) { this.boardId = boardId; }

    private Long eventId;
    private String name;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }


    @JsonProperty("isExternalOpen")
    private Boolean externalOpen;

    @JsonProperty("isUnfrozen")
    private Boolean unfrozen;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime actualStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime actualEndTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime freezeTime;
    private MedalConfig medalConfig;
    private List<ProblemConfig> problems;

    public LocalDateTime getFreezeTime() {
        return freezeTime;
    }

    public void setFreezeTime(LocalDateTime freezeTime) {
        this.freezeTime = freezeTime;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Boolean getExternalOpen() {
        return externalOpen;
    }

    public void setExternalOpen(Boolean externalOpen) {
        this.externalOpen = externalOpen;
    }

    public Boolean getUnfrozen() {
        return unfrozen;
    }

    public void setUnfrozen(Boolean unfrozen) {
        this.unfrozen = unfrozen;
    }

    public LocalDateTime getActualStartTime() {
        return actualStartTime;
    }

    public void setActualStartTime(LocalDateTime actualStartTime) {
        this.actualStartTime = actualStartTime;
    }

    public LocalDateTime getActualEndTime() {
        return actualEndTime;
    }

    public void setActualEndTime(LocalDateTime actualEndTime) {
        this.actualEndTime = actualEndTime;
    }

    public MedalConfig getMedalConfig() {
        return medalConfig;
    }

    public void setMedalConfig(MedalConfig medalConfig) {
        this.medalConfig = medalConfig;
    }

    public List<ProblemConfig> getProblems() {
        return problems;
    }

    public void setProblems(List<ProblemConfig> problems) {
        this.problems = problems;
    }

    public static class MedalConfig {
        private Integer gold;
        private Integer silver;
        private Integer bronze;

        public Integer getGold() {
            return gold;
        }

        public void setGold(Integer gold) {
            this.gold = gold;
        }

        public Integer getSilver() {
            return silver;
        }

        public void setSilver(Integer silver) {
            this.silver = silver;
        }

        public Integer getBronze() {
            return bronze;
        }

        public void setBronze(Integer bronze) {
            this.bronze = bronze;
        }
    }

    public static class ProblemConfig {
        private String displayId;
        private String name;
        private Integer score;
        private String color;
        private Integer timeLimit;
        private Integer memoryLimit;

        public String getDisplayId() {
            return displayId;
        }

        public void setDisplayId(String displayId) {
            this.displayId = displayId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getScore() {
            return score;
        }

        public void setScore(Integer score) {
            this.score = score;
        }

        public String getColor() {
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }

        public Integer getTimeLimit() {
            return timeLimit;
        }

        public void setTimeLimit(Integer timeLimit) {
            this.timeLimit = timeLimit;
        }

        public Integer getMemoryLimit() {
            return memoryLimit;
        }

        public void setMemoryLimit(Integer memoryLimit) {
            this.memoryLimit = memoryLimit;
        }
    }
}
