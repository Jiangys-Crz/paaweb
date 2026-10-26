package com.cwnupaa.backend.vo;

import java.util.List;
import java.util.Map;

public class BoardStatsVO {
    private Integer totalSubmissions;
    private Integer problemCount;
    private Integer participantCount;
    private Integer organizationCount;
    private List<List<Object>> heatmapData;
    private Map<String, Map<String, Integer>> problemStats;
    private Map<Integer, Integer> solvedDistribution;
    private List<SubmissionChartItem> submissionChart;
    private List<ProblemPassItem> problemPassChart;
    private List<TeamPassItem> teamPassChart;

    public Integer getTotalSubmissions() {
        return totalSubmissions;
    }

    public void setTotalSubmissions(Integer totalSubmissions) {
        this.totalSubmissions = totalSubmissions;
    }

    public Integer getProblemCount() {
        return problemCount;
    }

    public void setProblemCount(Integer problemCount) {
        this.problemCount = problemCount;
    }

    public Integer getParticipantCount() {
        return participantCount;
    }

    public void setParticipantCount(Integer participantCount) {
        this.participantCount = participantCount;
    }

    public Integer getOrganizationCount() {
        return organizationCount;
    }

    public void setOrganizationCount(Integer organizationCount) {
        this.organizationCount = organizationCount;
    }

    public List<List<Object>> getHeatmapData() {
        return heatmapData;
    }

    public void setHeatmapData(List<List<Object>> heatmapData) {
        this.heatmapData = heatmapData;
    }

    public Map<String, Map<String, Integer>> getProblemStats() {
        return problemStats;
    }

    public void setProblemStats(Map<String, Map<String, Integer>> problemStats) {
        this.problemStats = problemStats;
    }

    public Map<Integer, Integer> getSolvedDistribution() {
        return solvedDistribution;
    }

    public void setSolvedDistribution(Map<Integer, Integer> solvedDistribution) {
        this.solvedDistribution = solvedDistribution;
    }

    public List<SubmissionChartItem> getSubmissionChart() {
        return submissionChart;
    }

    public void setSubmissionChart(List<SubmissionChartItem> submissionChart) {
        this.submissionChart = submissionChart;
    }

    public List<ProblemPassItem> getProblemPassChart() {
        return problemPassChart;
    }

    public void setProblemPassChart(List<ProblemPassItem> problemPassChart) {
        this.problemPassChart = problemPassChart;
    }

    public List<TeamPassItem> getTeamPassChart() {
        return teamPassChart;
    }

    public void setTeamPassChart(List<TeamPassItem> teamPassChart) {
        this.teamPassChart = teamPassChart;
    }

    public static class SubmissionChartItem {
        private final String problemId;
        private final Integer acCount;
        private final Integer waCount;

        public SubmissionChartItem(String problemId, Integer acCount, Integer waCount) {
            this.problemId = problemId;
            this.acCount = acCount;
            this.waCount = waCount;
        }

        public String getProblemId() {
            return problemId;
        }

        public Integer getAcCount() {
            return acCount;
        }

        public Integer getWaCount() {
            return waCount;
        }
    }

    public static class ProblemPassItem {
        private final String problemId;
        private final Integer passCount;

        public ProblemPassItem(String problemId, Integer passCount) {
            this.problemId = problemId;
            this.passCount = passCount;
        }

        public String getProblemId() {
            return problemId;
        }

        public Integer getPassCount() {
            return passCount;
        }
    }

    public static class TeamPassItem {
        private final Integer solvedCount;
        private final Integer teamCount;

        public TeamPassItem(Integer solvedCount, Integer teamCount) {
            this.solvedCount = solvedCount;
            this.teamCount = teamCount;
        }

        public Integer getSolvedCount() {
            return solvedCount;
        }

        public Integer getTeamCount() {
            return teamCount;
        }
    }
}