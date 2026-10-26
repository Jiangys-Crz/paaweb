package com.cwnupaa.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

@TableName("training")
public class Training {
    @TableId(type = IdType.AUTO)
    private Long trainId;
    private String name;
    private Long termId;
    private Long teacherId;
    private Long publisherId;
    private String publisherName;
    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime signupStart;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime signupEnd;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime trainStart;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime trainEnd;

    private Integer maxCount;

    public Long getTrainId() {
        return trainId;
    }

    public void setTrainId(Long trainId) {
        this.trainId = trainId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getTermId() {
        return termId;
    }

    public void setTermId(Long termId) {
        this.termId = termId;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }

    public Long getPublisherId() {
        return publisherId;
    }

    public void setPublisherId(Long publisherId) {
        this.publisherId = publisherId;
    }

    public String getPublisherName() {
        return publisherName;
    }

    public void setPublisherName(String publisherName) {
        this.publisherName = publisherName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getSignupStart() {
        return signupStart;
    }

    public void setSignupStart(LocalDateTime signupStart) {
        this.signupStart = signupStart;
    }

    public LocalDateTime getSignupEnd() {
        return signupEnd;
    }

    public void setSignupEnd(LocalDateTime signupEnd) {
        this.signupEnd = signupEnd;
    }

    public LocalDateTime getTrainStart() {
        return trainStart;
    }

    public void setTrainStart(LocalDateTime trainStart) {
        this.trainStart = trainStart;
    }

    public LocalDateTime getTrainEnd() {
        return trainEnd;
    }

    public void setTrainEnd(LocalDateTime trainEnd) {
        this.trainEnd = trainEnd;
    }

    public Integer getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(Integer maxCount) {
        this.maxCount = maxCount;
    }
}
