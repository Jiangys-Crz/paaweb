package com.cwnupaa.backend.vo;

import java.util.List;

public class TrackerDashboardVO {
    private Long totalSolved;
    private Long last7DaysSolved;
    private Long last30DaysSolved;
    private Long lastYearSolved;
    private List<PlatformStatVO> platformStats;
    private ChartScopeVO scope7Days;
    private ChartScopeVO scope30Days;
    private ChartScopeVO scope1Year;
    private List<List<Object>> dailyHeatmap;
    private List<List<Object>> maxDifficultyHeatmap;

    public Long getTotalSolved() {
        return totalSolved;
    }

    public void setTotalSolved(Long totalSolved) {
        this.totalSolved = totalSolved;
    }

    public Long getLast7DaysSolved() {
        return last7DaysSolved;
    }

    public void setLast7DaysSolved(Long last7DaysSolved) {
        this.last7DaysSolved = last7DaysSolved;
    }

    public Long getLast30DaysSolved() {
        return last30DaysSolved;
    }

    public void setLast30DaysSolved(Long last30DaysSolved) {
        this.last30DaysSolved = last30DaysSolved;
    }

    public Long getLastYearSolved() {
        return lastYearSolved;
    }

    public void setLastYearSolved(Long lastYearSolved) {
        this.lastYearSolved = lastYearSolved;
    }

    public List<PlatformStatVO> getPlatformStats() {
        return platformStats;
    }

    public void setPlatformStats(List<PlatformStatVO> platformStats) {
        this.platformStats = platformStats;
    }

    public ChartScopeVO getScope7Days() {
        return scope7Days;
    }

    public void setScope7Days(ChartScopeVO scope7Days) {
        this.scope7Days = scope7Days;
    }

    public ChartScopeVO getScope30Days() {
        return scope30Days;
    }

    public void setScope30Days(ChartScopeVO scope30Days) {
        this.scope30Days = scope30Days;
    }

    public ChartScopeVO getScope1Year() {
        return scope1Year;
    }

    public void setScope1Year(ChartScopeVO scope1Year) {
        this.scope1Year = scope1Year;
    }

    public List<List<Object>> getDailyHeatmap() {
        return dailyHeatmap;
    }

    public void setDailyHeatmap(List<List<Object>> dailyHeatmap) {
        this.dailyHeatmap = dailyHeatmap;
    }

    public List<List<Object>> getMaxDifficultyHeatmap() {
        return maxDifficultyHeatmap;
    }

    public void setMaxDifficultyHeatmap(List<List<Object>> maxDifficultyHeatmap) {
        this.maxDifficultyHeatmap = maxDifficultyHeatmap;
    }
}