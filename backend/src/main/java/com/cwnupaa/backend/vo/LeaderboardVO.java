package com.cwnupaa.backend.vo;

public class LeaderboardVO {
    private Integer rank;
    private Long userId;
    private String nickname;
    private String realName;
    private String avatar;
    private String bio;

    private String role;

    private Integer ratingAll;
    private Integer ratingContest;
    private Integer ratingCertificate;
    private Integer ratingPractice;

    public Integer getRank() {
        return rank;
    }

    public void setRank(Integer rank) {
        this.rank = rank;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Integer getRatingAll() {
        return ratingAll;
    }

    public void setRatingAll(Integer ratingAll) {
        this.ratingAll = ratingAll;
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
}