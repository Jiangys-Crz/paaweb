package com.cwnupaa.backend.vo;

import java.time.LocalDateTime;
import java.util.List;

public class BoardVO {
    private Long boardId;

    public Long getBoardId() { return boardId; }
    public void setBoardId(Long boardId) { this.boardId = boardId; }

    private Long eventId;
    private String title;
    private String sessionName;
    public String getSessionName() { return sessionName; }
    public void setSessionName(String value) { sessionName = value; }
    private String type;
    private String status;
    private boolean unfrozen;
    private boolean resolverEnabled;

    public boolean isResolverEnabled() { return resolverEnabled; }
    public void setResolverEnabled(boolean enabled) { resolverEnabled = enabled; }
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String freezeTime;
    private Integer isTeam;

    private List<ProblemInfo> problems;
    private List<RankItemVO> rows;

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isUnfrozen() {
        return unfrozen;
    }

    public void setUnfrozen(boolean unfrozen) {
        this.unfrozen = unfrozen;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public String getFreezeTime() {
        return freezeTime;
    }

    public void setFreezeTime(String freezeTime) {
        this.freezeTime = freezeTime;
    }

    public Integer getIsTeam() {
        return isTeam;
    }

    public void setIsTeam(Integer isTeam) {
        this.isTeam = isTeam;
    }

    public List<ProblemInfo> getProblems() {
        return problems;
    }

    public void setProblems(List<ProblemInfo> problems) {
        this.problems = problems;
    }

    public List<RankItemVO> getRows() {
        return rows;
    }

    public void setRows(List<RankItemVO> rows) {
        this.rows = rows;
    }

    public static class ProblemInfo {
        private String id;
        private String name;
        private Integer score;
        private Integer timeLimit;
        private Integer memoryLimit;

        private Integer submitted = 0;
        private Integer attempted = 0;
        private Integer accepted = 0;

        private Long firstSolved = -1L;
        private Long lastSolved = -1L;

        private Double avgScore = 0.0;
        private Integer maxScore = 0;
        private Integer minScore = 0;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
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

        public Integer getSubmitted() {
            return submitted;
        }

        public void setSubmitted(Integer submitted) {
            this.submitted = submitted;
        }

        public Integer getAttempted() {
            return attempted;
        }

        public void setAttempted(Integer attempted) {
            this.attempted = attempted;
        }

        public Integer getAccepted() {
            return accepted;
        }

        public void setAccepted(Integer accepted) {
            this.accepted = accepted;
        }

        public Long getFirstSolved() {
            return firstSolved;
        }

        public void setFirstSolved(Long firstSolved) {
            this.firstSolved = firstSolved;
        }

        public Long getLastSolved() {
            return lastSolved;
        }

        public void setLastSolved(Long lastSolved) {
            this.lastSolved = lastSolved;
        }

        public Double getAvgScore() {
            return avgScore;
        }

        public void setAvgScore(Double avgScore) {
            this.avgScore = avgScore;
        }

        public Integer getMaxScore() {
            return maxScore;
        }

        public void setMaxScore(Integer maxScore) {
            this.maxScore = maxScore;
        }

        public Integer getMinScore() {
            return minScore;
        }

        public void setMinScore(Integer minScore) {
            this.minScore = minScore;
        }
    }
}
