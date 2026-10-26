package com.cwnupaa.backend.dto;

import lombok.Data;

@Data
public class UserAggregatedStatsDTO {
    private Long userCount;
    private Long totalSolved;
    private Long last30DaysSolved;
    private Long last7DaysSolved;
}
