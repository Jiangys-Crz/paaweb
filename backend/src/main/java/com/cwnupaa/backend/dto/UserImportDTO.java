package com.cwnupaa.backend.dto;

import lombok.Data;

@Data
public class UserImportDTO {
    private String realName;
    private String numberId;
    private String password; // 初始密码（导入时设置；留空则系统随机生成）
    private String role; // "学生", "老师", "部门"
    private String eduBg; // "研究生", "本科生"
    private String universityName; // "学校"
    private String schoolName; // "学院"
    private String majorName; // "专业"
    private String gradeYear; // "年级"
    private String className; // "班级"
}
