package com.cwnupaa.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("certificate_issue_member")
public class CertificateIssueMember {
    @TableId(type = IdType.AUTO)
    private Long issueMemberId;
    private Long issueId;
    private String memberName;
    private String studentId;
    private String schoolName; // 成员所属学校（大学）名称，配合学号匹配用户
    private Long userId;
    private Integer sortOrder;

    public Long getIssueMemberId() { return issueMemberId; }
    public void setIssueMemberId(Long issueMemberId) { this.issueMemberId = issueMemberId; }
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
