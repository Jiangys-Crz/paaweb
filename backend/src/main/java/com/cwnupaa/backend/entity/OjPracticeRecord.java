package com.cwnupaa.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("oj_practice_record")
public class OjPracticeRecord {
    @TableId(type = IdType.AUTO)
    private Long practiceProblemId;

    private Long userId;
    private Long sourceId;
    private String problemId;
    private String problemName;
    private String problemDifficulty;
    private String problemTag;
    private LocalDateTime acTime;

    public Long getPracticeProblemId() {
        return practiceProblemId;
    }

    public void setPracticeProblemId(Long practiceProblemId) {
        this.practiceProblemId = practiceProblemId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
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

    public String getProblemDifficulty() {
        return problemDifficulty;
    }

    public void setProblemDifficulty(String problemDifficulty) {
        this.problemDifficulty = problemDifficulty;
    }

    public String getProblemTag() {
        return problemTag;
    }

    public void setProblemTag(String problemTag) {
        this.problemTag = problemTag;
    }

    public LocalDateTime getAcTime() {
        return acTime;
    }

    public void setAcTime(LocalDateTime acTime) {
        this.acTime = acTime;
    }
}