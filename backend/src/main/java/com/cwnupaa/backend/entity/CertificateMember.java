package com.cwnupaa.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("certificate_member")
public class CertificateMember {
    @TableId(type = IdType.AUTO)
    private Long memberId;
    private Long certificateVerifyId;
    private Long userId;
    private String name;
    private String studentId;
    private String schoolName;

    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String value) { schoolName = value; }
    private Integer isCaptain;
    private Integer sortOrder;

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Long getCertificateVerifyId() {
        return certificateVerifyId;
    }

    public void setCertificateVerifyId(Long certificateVerifyId) {
        this.certificateVerifyId = certificateVerifyId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public Integer getIsCaptain() {
        return isCaptain;
    }

    public void setIsCaptain(Integer isCaptain) {
        this.isCaptain = isCaptain;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
