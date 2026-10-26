package com.cwnupaa.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("oj_recent_contest")
public class OjRecentContest {
    @TableId(type = IdType.AUTO)
    private Long upcomingContestsId;
    private Long sourceId;

    private String contestsId;

    private String contestsName;

    private LocalDateTime contestsStarttime;

    private LocalDateTime contestsEndtime;
    private LocalDateTime registrationStart;
    private LocalDateTime registrationEnd;

    private String url;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Long getUpcomingContestsId() {
        return upcomingContestsId;
    }

    public void setUpcomingContestsId(Long upcomingContestsId) {
        this.upcomingContestsId = upcomingContestsId;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public String getContestsId() {
        return contestsId;
    }

    public void setContestsId(String contestsId) {
        this.contestsId = contestsId;
    }

    public String getContestsName() {
        return contestsName;
    }

    public void setContestsName(String contestsName) {
        this.contestsName = contestsName;
    }

    public LocalDateTime getContestsStarttime() {
        return contestsStarttime;
    }

    public void setContestsStarttime(LocalDateTime contestsStarttime) {
        this.contestsStarttime = contestsStarttime;
    }

    public LocalDateTime getContestsEndtime() { return contestsEndtime; }

    public void setContestsEndtime(LocalDateTime contestsEndtime) { this.contestsEndtime = contestsEndtime; }

    public LocalDateTime getRegistrationStart() { return registrationStart; }
    public void setRegistrationStart(LocalDateTime registrationStart) { this.registrationStart = registrationStart; }
    public LocalDateTime getRegistrationEnd() { return registrationEnd; }
    public void setRegistrationEnd(LocalDateTime registrationEnd) { this.registrationEnd = registrationEnd; }
}
