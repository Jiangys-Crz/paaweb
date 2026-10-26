package com.cwnupaa.backend.vo;

import lombok.Data;
import java.util.List;

@Data
public class CertificateStatsVO {
    private Integer totalContestTypes; // 参加比赛类型总数
    private Integer totalUsers; // 用户总数
    private Integer totalFirstPrize; // 获得一等奖总数
    private Integer totalSecondPrize; // 获得二等奖总数
    private Integer totalThirdPrize; // 获得三等奖总数
    private Integer totalOtherPrize; // 获得其他奖总数
    private Integer totalAwards; // 获得奖项总数
    
    private List<CertificateDetailVO> list;
}
