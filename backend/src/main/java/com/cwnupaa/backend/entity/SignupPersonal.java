package com.cwnupaa.backend.entity;

import com.cwnupaa.backend.utils.AdvisorNames;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("signup_personal")
public class SignupPersonal {
    @TableId(type = IdType.AUTO)
    private Long signupId;
    private Long eventId;
    private Long userId;
    private String status;
    // First submission time; cancellation and reapplication must preserve it.
    private LocalDateTime signupTime;

    private Integer isRanking;
    private String advisor;
    private String contestAccount;
    private String contestPassword;
    private String seatNumber;
    private String type;
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

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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
        return AdvisorNames.normalize(advisor);
    }

    public void setAdvisor(String advisor) {
        this.advisor = AdvisorNames.normalize(advisor);
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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
