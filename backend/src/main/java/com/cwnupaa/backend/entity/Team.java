package com.cwnupaa.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@TableName("team")
public class Team {
    @TableId(type = IdType.AUTO)
    private Long teamId;

    @NotBlank(message = "队伍名称不能为空")
    @Size(max = 20, message = "队伍名称不能超过20字")
    private String teamName;

    @Size(max = 100, message = "队伍宣言不能超过100字")
    private String introduce;
    private Long leaderId;
    private Long teamImageId;

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

    public String getIntroduce() {
        return introduce;
    }

    public void setIntroduce(String introduce) {
        this.introduce = introduce;
    }

    public Long getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(Long leaderId) {
        this.leaderId = leaderId;
    }

    public Long getTeamImageId() {
        return teamImageId;
    }

    public void setTeamImageId(Long teamImageId) {
        this.teamImageId = teamImageId;
    }
}