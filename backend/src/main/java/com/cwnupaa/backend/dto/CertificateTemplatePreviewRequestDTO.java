package com.cwnupaa.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

public class CertificateTemplatePreviewRequestDTO {
    private Long fileId;
    private String templateSchema;
    private Integer isTeam;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate issueDate;

    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public String getTemplateSchema() { return templateSchema; }
    public void setTemplateSchema(String templateSchema) { this.templateSchema = templateSchema; }
    public Integer getIsTeam() { return isTeam; }
    public void setIsTeam(Integer isTeam) { this.isTeam = isTeam; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
}
