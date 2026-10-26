package com.cwnupaa.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("rating_term_record")
public class RatingTermRecord {
    @TableId(type = IdType.AUTO)
    private Long ratingRecordId;
    private Long termId;
    private Long userId;
    private Integer ratingContest;
    private Integer ratingCertificate;
    private Integer ratingPractice;
    private Integer ratingAll;

    public Long getRatingRecordId() {
        return ratingRecordId;
    }

    public void setRatingRecordId(Long ratingRecordId) {
        this.ratingRecordId = ratingRecordId;
    }

    public Long getTermId() {
        return termId;
    }

    public void setTermId(Long termId) {
        this.termId = termId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getRatingContest() {
        return ratingContest;
    }

    public void setRatingContest(Integer ratingContest) {
        this.ratingContest = ratingContest;
    }

    public Integer getRatingCertificate() {
        return ratingCertificate;
    }

    public void setRatingCertificate(Integer ratingCertificate) {
        this.ratingCertificate = ratingCertificate;
    }

    public Integer getRatingPractice() {
        return ratingPractice;
    }

    public void setRatingPractice(Integer ratingPractice) {
        this.ratingPractice = ratingPractice;
    }

    public Integer getRatingAll() {
        return ratingAll;
    }

    public void setRatingAll(Integer ratingAll) {
        this.ratingAll = ratingAll;
    }
}