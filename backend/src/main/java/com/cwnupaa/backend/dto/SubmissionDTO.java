package com.cwnupaa.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class SubmissionDTO {
    private String platform;

    @JsonProperty("remote_id") // 映射 snake_case
    private String remoteId;

    @JsonProperty("problem_id")
    private String problemId;

    private String title;

    private String link;

    @JsonProperty("solve_time")
    private Long solveTime;

    private Double difficulty;

    @JsonProperty("raw_difficulty")
    private String rawDifficulty;

    private List<String> tags;

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getRemoteId() {
        return remoteId;
    }

    public void setRemoteId(String remoteId) {
        this.remoteId = remoteId;
    }

    public String getProblemId() {
        return problemId;
    }

    public void setProblemId(String problemId) {
        this.problemId = problemId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public Long getSolveTime() {
        return solveTime;
    }

    public void setSolveTime(Long solveTime) {
        this.solveTime = solveTime;
    }

    public Double getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Double difficulty) {
        this.difficulty = difficulty;
    }

    public String getRawDifficulty() {
        return rawDifficulty;
    }

    public void setRawDifficulty(String rawDifficulty) {
        this.rawDifficulty = rawDifficulty;
    }
}