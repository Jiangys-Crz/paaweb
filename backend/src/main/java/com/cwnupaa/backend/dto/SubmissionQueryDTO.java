package com.cwnupaa.backend.dto;

import java.time.LocalDate;

public class SubmissionQueryDTO {
    private Long userId;
    private Long sourceId;
    private String difficultyRange;
    private String keyword;
    private String problemId;
    private LocalDate startTime;
    private LocalDate endTime;

    private int page = 1;
    private int size = 20;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public String getDifficultyRange() {
        return difficultyRange;
    }

    public void setDifficultyRange(String difficultyRange) {
        this.difficultyRange = difficultyRange;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getProblemId() {
        return problemId;
    }

    public void setProblemId(String problemId) {
        this.problemId = problemId;
    }

    public LocalDate getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDate startTime) {
        this.startTime = startTime;
    }

    public LocalDate getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDate endTime) {
        this.endTime = endTime;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}