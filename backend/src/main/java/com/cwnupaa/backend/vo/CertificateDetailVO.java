package com.cwnupaa.backend.vo;

import lombok.Data;

@Data
public class CertificateDetailVO {
    private Integer no; // 序号
    private String schoolName; // 学院 (from Major -> School)
    private String contestName; // 竞赛名称
    private String contestLevel; // 竞赛级别
    private String awardLevel; // 获奖等级
    private String advisor; // 第一指导老师
    private String studentNames; // 获奖学生
    private String studentIds; // 获奖学生学号
    private String awardDate; // 获奖年月
    private String teamOrIndividual; // 团队或个人
    private String remark; // 备注 (子项目名称)
    private String certificateNo; // 可验真证书编号；旧证书为空
    
    // Internal use for export
    private Long fileId;
    private String exportFileName; // 序号+竞赛名称+获奖等级+第一指导老师
}
