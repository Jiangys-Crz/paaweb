package com.cwnupaa.backend.vo;

import com.cwnupaa.backend.utils.AdvisorNames;
import java.time.LocalDateTime;

public class EventSignupVO {
    private Long signupId;
    private Long eventId;
    private Long userId;
    private Long teamId;
    private String teamName;
    private LocalDateTime signupTime;
    private String status;
    private String realName;
    private String gender;         // 🔴 新增：性别 (男/女)
    private String numberId;
    private String className;
    private String gradeYear;      // 🔴 新增：年级
    private String majorName;
    private String schoolName;
    private String universityName;
    private String organization;
    private String organizationLogo;

    public String getOrganization() { return organization; }
    public void setOrganization(String value) { organization = value; }
    public String getOrganizationLogo() { return organizationLogo; }
    public void setOrganizationLogo(String value) { organizationLogo = value; }

    private String membersContent;

    private String advisor;
    private Integer isRanking;
    private String teamType;
    private String contestAccount;
    private String contestPassword;
    private String seatNumber;
    private Long createdByUserId;
    private String createdByName;
    private String signupSource;

    public Long getSignupId() {
        return signupId;
    }

    public void setSignupId(Long signupId) {
        this.signupId = signupId;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getTeamId() {
        return teamId;
    }

    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public LocalDateTime getSignupTime() {
        return signupTime;
    }

    public void setSignupTime(LocalDateTime signupTime) {
        this.signupTime = signupTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getNumberId() {
        return numberId;
    }

    public void setNumberId(String numberId) {
        this.numberId = numberId;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getGradeYear() {
        return gradeYear;
    }

    public void setGradeYear(String gradeYear) {
        this.gradeYear = gradeYear;
    }

    public String getMajorName() {
        return majorName;
    }

    public void setMajorName(String majorName) {
        this.majorName = majorName;
    }

    public String getSchoolName() {
        return schoolName;
    }

    public void setSchoolName(String schoolName) {
        this.schoolName = schoolName;
    }

    public String getUniversityName() {
        return universityName;
    }

    public void setUniversityName(String universityName) {
        this.universityName = universityName;
    }

    public String getMembersContent() {
        return membersContent;
    }

    public void setMembersContent(String membersContent) {
        this.membersContent = membersContent;
    }

    public String getAdvisor() {
        return AdvisorNames.normalize(advisor);
    }

    public void setAdvisor(String advisor) {
        this.advisor = AdvisorNames.normalize(advisor);
    }

    public Integer getIsRanking() {
        return isRanking;
    }

    public void setIsRanking(Integer isRanking) {
        this.isRanking = isRanking;
    }

    public String getTeamType() {
        return teamType;
    }

    public void setTeamType(String teamType) {
        this.teamType = teamType;
    }

    public String getContestAccount() {
        return contestAccount;
    }

    public void setContestAccount(String contestAccount) {
        this.contestAccount = contestAccount;
    }

    public String getContestPassword() {
        return contestPassword;
    }

    public void setContestPassword(String contestPassword) {
        this.contestPassword = contestPassword;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(String seatNumber) {
        this.seatNumber = seatNumber;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(Long createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public String getSignupSource() {
        return signupSource;
    }

    public void setSignupSource(String signupSource) {
        this.signupSource = signupSource;
    }
}
