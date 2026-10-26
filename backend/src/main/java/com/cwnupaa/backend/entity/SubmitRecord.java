package com.cwnupaa.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("submit_record")
public class SubmitRecord {
    @TableId(type = IdType.AUTO)
    private Long submitRecordId;

    private Long boardId;

    public Long getBoardId() { return boardId; }
    public void setBoardId(Long boardId) { this.boardId = boardId; }

    private Long eventId;
    private Long userId;
    private Long signupTeamId;
    private String problemId;
    private String problemName;
    private String language;
    private String codeContent;

    private Integer codeLength;
    private Integer timeUsage;
    private Integer memoryUsage;

    private String rid;

    private LocalDateTime submitTime;
    private String result;
    private Integer score;

    public Long getSubmitRecordId() {
        return submitRecordId;
    }

    public void setSubmitRecordId(Long submitRecordId) {
        this.submitRecordId = submitRecordId;
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

    public Long getSignupTeamId() {
        return signupTeamId;
    }

    public void setSignupTeamId(Long signupTeamId) {
        this.signupTeamId = signupTeamId;
    }

    public String getProblemId() {
        return problemId;
    }

    public void setProblemId(String problemId) {
        this.problemId = problemId;
    }

    public String getProblemName() {
        return problemName;
    }

    public void setProblemName(String problemName) {
        this.problemName = problemName;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getCodeContent() {
        return codeContent;
    }

    public void setCodeContent(String codeContent) {
        this.codeContent = codeContent;
    }

    public Integer getCodeLength() {
        return codeLength;
    }

    public void setCodeLength(Integer codeLength) {
        this.codeLength = codeLength;
    }

    public Integer getTimeUsage() {
        return timeUsage;
    }

    public void setTimeUsage(Integer timeUsage) {
        this.timeUsage = timeUsage;
    }

    public Integer getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(Integer memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public String getRid() {
        return rid;
    }

    public void setRid(String rid) {
        this.rid = rid;
    }

    public LocalDateTime getSubmitTime() {
        return submitTime;
    }

    public void setSubmitTime(LocalDateTime submitTime) {
        this.submitTime = submitTime;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }
}
