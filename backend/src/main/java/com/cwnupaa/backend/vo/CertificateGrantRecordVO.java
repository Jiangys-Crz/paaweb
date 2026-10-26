package com.cwnupaa.backend.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.time.LocalDate;

public class CertificateGrantRecordVO {
    private Long certificateDownloadId;
    private String userAccount;
    private String realName;
    private String customName;
    private Long issueId;
    private String certificateNo;
    private String levelScope;
    private String awardName;
    private String teamName;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate issueDate;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createTime;

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public Long getCertificateDownloadId() {
        return certificateDownloadId;
    }

    public void setCertificateDownloadId(Long certificateDownloadId) {
        this.certificateDownloadId = certificateDownloadId;
    }

    public String getUserAccount() {
        return userAccount;
    }

    public void setUserAccount(String userAccount) {
        this.userAccount = userAccount;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public String getCertificateNo() { return certificateNo; }
    public void setCertificateNo(String certificateNo) { this.certificateNo = certificateNo; }
    public String getLevelScope() { return levelScope; }
    public void setLevelScope(String levelScope) { this.levelScope = levelScope; }
    public String getAwardName() { return awardName; }
    public void setAwardName(String awardName) { this.awardName = awardName; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
