package com.cwnupaa.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("certificate_grant")
public class CertificateGrant {
    @TableId(type = IdType.AUTO)
    private Long certificateDownloadId;
    private Long certificateTypeId;
    private Long userId;
    private LocalDateTime createTime;
    private String customName; // For team certificate display
    private String userAccount; // To store student ID for unregistered users
    private String realName; // To store name for unregistered users
    private String schoolName; // 获奖人所属学校（大学）名称，配合学号匹配用户
    private String groupId; // To identify team members for bulk revocation
    private Long issueId; // Null for legacy grants without authenticity verification

    public Long getCertificateDownloadId() {
        return certificateDownloadId;
    }

    public void setCertificateDownloadId(Long certificateDownloadId) {
        this.certificateDownloadId = certificateDownloadId;
    }

    public Long getCertificateTypeId() {
        return certificateTypeId;
    }

    public void setCertificateTypeId(Long certificateTypeId) {
        this.certificateTypeId = certificateTypeId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
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

    public String getSchoolName() {
        return schoolName;
    }

    public void setSchoolName(String schoolName) {
        this.schoolName = schoolName;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public Long getIssueId() {
        return issueId;
    }

    public void setIssueId(Long issueId) {
        this.issueId = issueId;
    }
}
