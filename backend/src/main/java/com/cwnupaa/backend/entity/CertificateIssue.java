package com.cwnupaa.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("certificate_issue")
public class CertificateIssue {
    @TableId(type = IdType.AUTO)
    private Long issueId;
    private Long certificateTypeId;
    private Long templateFileId;
    private String certificateNo;
    private Integer certificateKind;
    @TableField("level_scope")
    private String levelScope;
    private String teamName;
    private String awardName;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate issueDate;
    private String status;
    private String templateSchemaSnapshot;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime revokedAt;

    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public Long getCertificateTypeId() { return certificateTypeId; }
    public void setCertificateTypeId(Long certificateTypeId) { this.certificateTypeId = certificateTypeId; }
    public Long getTemplateFileId() { return templateFileId; }
    public void setTemplateFileId(Long templateFileId) { this.templateFileId = templateFileId; }
    public String getCertificateNo() { return certificateNo; }
    public void setCertificateNo(String certificateNo) { this.certificateNo = certificateNo; }
    public Integer getCertificateKind() { return certificateKind; }
    public void setCertificateKind(Integer certificateKind) { this.certificateKind = certificateKind; }
    public String getLevelScope() { return levelScope; }
    public void setLevelScope(String levelScope) { this.levelScope = levelScope; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public String getAwardName() { return awardName; }
    public void setAwardName(String awardName) { this.awardName = awardName; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTemplateSchemaSnapshot() { return templateSchemaSnapshot; }
    public void setTemplateSchemaSnapshot(String templateSchemaSnapshot) { this.templateSchemaSnapshot = templateSchemaSnapshot; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
}
