package com.cwnupaa.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("signup_team_detail")
public class SignupTeamDetail {
    @TableId(type = IdType.AUTO)
    private Long detailId;

    private Long signupTeamId;
    private Long userId;
    private Integer memberOrder;

    private String contestAccount;
    private String contestPassword;
    private String seatNumber;

    public Long getDetailId() {
        return detailId;
    }

    public void setDetailId(Long detailId) {
        this.detailId = detailId;
    }

    public Long getSignupTeamId() {
        return signupTeamId;
    }

    public void setSignupTeamId(Long signupTeamId) {
        this.signupTeamId = signupTeamId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getMemberOrder() {
        return memberOrder;
    }

    public void setMemberOrder(Integer memberOrder) {
        this.memberOrder = memberOrder;
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
}
