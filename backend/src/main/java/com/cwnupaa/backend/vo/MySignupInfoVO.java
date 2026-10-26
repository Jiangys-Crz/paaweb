package com.cwnupaa.backend.vo;

import java.time.LocalDateTime;
import java.util.List;

public class MySignupInfoVO {
    private String status;
    private String signupType;
    private LocalDateTime signupTime;
    private Integer isRanking;
    private String advisor;
    private String teamName;
    private String teamTypeDisplay;
    private String contestAccount;
    private String contestPassword;
    private String seatNumber;
    private List<MemberCredential> memberCredentials;
    private List<SimpleMemberInfo> members;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSignupType() {
        return signupType;
    }

    public void setSignupType(String signupType) {
        this.signupType = signupType;
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

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getTeamTypeDisplay() {
        return teamTypeDisplay;
    }

    public void setTeamTypeDisplay(String teamTypeDisplay) {
        this.teamTypeDisplay = teamTypeDisplay;
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

    public List<MemberCredential> getMemberCredentials() {
        return memberCredentials;
    }

    public void setMemberCredentials(List<MemberCredential> memberCredentials) {
        this.memberCredentials = memberCredentials;
    }

    public List<SimpleMemberInfo> getMembers() {
        return members;
    }

    public void setMembers(List<SimpleMemberInfo> members) {
        this.members = members;
    }

    public static class MemberCredential {
        private String realName;
        private String numberId;
        private String contestAccount;
        private String contestPassword;
        private String seatNumber;

        public String getRealName() {
            return realName;
        }

        public void setRealName(String realName) {
            this.realName = realName;
        }

        public String getNumberId() {
            return numberId;
        }

        public void setNumberId(String numberId) {
            this.numberId = numberId;
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

    public static class SimpleMemberInfo {
        private Long userId;
        private String userName;
        private String avatarUrl;
        private String realName;
        private String numberId;
        private boolean isCaptain;
        private String schoolName;
        private String majorName;
        private String gradeYear;
        private String universityName;
        private String idPhotoUrl;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

        public String getRealName() { return realName; }
        public void setRealName(String realName) { this.realName = realName; }
        public String getNumberId() { return numberId; }
        public void setNumberId(String numberId) { this.numberId = numberId; }
        public boolean isCaptain() { return isCaptain; }
        public void setCaptain(boolean captain) { isCaptain = captain; }
        public String getSchoolName() { return schoolName; }
        public void setSchoolName(String schoolName) { this.schoolName = schoolName; }
        public String getMajorName() { return majorName; }
        public void setMajorName(String majorName) { this.majorName = majorName; }
        public String getGradeYear() { return gradeYear; }
        public void setGradeYear(String gradeYear) { this.gradeYear = gradeYear; }
        public String getUniversityName() { return universityName; }
        public void setUniversityName(String universityName) { this.universityName = universityName; }
        public String getIdPhotoUrl() { return idPhotoUrl; }
        public void setIdPhotoUrl(String idPhotoUrl) { this.idPhotoUrl = idPhotoUrl; }
    }
}
