package com.cwnupaa.backend.vo;

import java.io.Serializable;
import java.util.List;

public class TrackerStatsVO implements Serializable {
    private Integer totalSolved;
    private Integer todaySolved;

    private List<List<Object>> heatmapData;

    private List<PlatformStatVO> platformStats; // 各平台详细分布

    public TrackerStatsVO() {
    }

    public Integer getTotalSolved() {
        return totalSolved;
    }

    public void setTotalSolved(Integer totalSolved) {
        this.totalSolved = totalSolved;
    }

    public Integer getTodaySolved() {
        return todaySolved;
    }

    public void setTodaySolved(Integer todaySolved) {
        this.todaySolved = todaySolved;
    }

    public List<List<Object>> getHeatmapData() {
        return heatmapData;
    }

    public void setHeatmapData(List<List<Object>> heatmapData) {
        this.heatmapData = heatmapData;
    }

    public List<PlatformStatVO> getPlatformStats() {
        return platformStats;
    }

    public void setPlatformStats(List<PlatformStatVO> platformStats) {
        this.platformStats = platformStats;
    }
}