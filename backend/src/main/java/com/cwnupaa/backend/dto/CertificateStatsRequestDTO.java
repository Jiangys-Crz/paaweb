package com.cwnupaa.backend.dto;

import lombok.Data;

@Data
public class CertificateStatsRequestDTO {
    private Long contestType; // categoryId
    private Long termId;
    private Long universityId; // "学校"
    private Long schoolId; // "学院"
    private Long majorId;
    private String grade; // "年级" e.g. "2021"
    private String className; // "班级"
    private String degreeType; // "本科"/"研究生" -> eduBg
    private String minLevel; // Minimum certificate level (e.g., "校级")
    private String keyword; // Search student name/id
}
