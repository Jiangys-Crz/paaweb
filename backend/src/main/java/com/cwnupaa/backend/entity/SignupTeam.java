package com.cwnupaa.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("signup_team")
public class SignupTeam {
    @TableId(type = IdType.AUTO)
    private Long signupId;
    private Long eventId;
    // Provenance only: identifies the source team across renames/reapplications.
    // No FK: deleting a source team must not remove historical signup snapshots.
    private Long sourceTeamId;
    private String teamName;
    private String status;
    // First submission time; cancellation and reapplication must preserve it.
    private LocalDateTime signupTime;
    private Integer isRanking;
    private String advisor;
    private String type;
    private String contestAccount;
    private String contestPassword;
    private String seatNumber;
    private Long createdByUserId;
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

    public Long getSourceTeamId() { return sourceTeamId; }

    public void setSourceTeamId(Long sourceTeamId) { this.sourceTeamId = sourceTeamId; }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getSignupTime() {
        return signupTime;
    }

    public void setSignupTime(LocalDateTime signupTime) {
        this.signupTime = signupTime;
    }

    public Integer getIsRanking() {
        return isRanking;
    }

    public void setIsRanking(Integer isRanking) {
        this.isRanking = isRanking;
    }

    public String getAdvisor() {
        return advisor;
    }

    public void setAdvisor(String advisor) {
        this.advisor = advisor;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public String getSignupSource() {
        return signupSource;
    }

    public void setSignupSource(String signupSource) {
        this.signupSource = signupSource;
    }
}
