package com.cwnupaa.backend.vo;

import java.util.List;

public class ChartScopeVO {
    private List<Integer> difficultyDistribution;

    private List<String> timeLabels;

    private List<Integer> timeValues;

    public List<Integer> getDifficultyDistribution() {
        return difficultyDistribution;
    }

    public void setDifficultyDistribution(List<Integer> difficultyDistribution) {
        this.difficultyDistribution = difficultyDistribution;
    }

    public List<String> getTimeLabels() {
        return timeLabels;
    }

    public void setTimeLabels(List<String> timeLabels) {
        this.timeLabels = timeLabels;
    }

    public List<Integer> getTimeValues() {
        return timeValues;
    }

    public void setTimeValues(List<Integer> timeValues) {
        this.timeValues = timeValues;
    }
}