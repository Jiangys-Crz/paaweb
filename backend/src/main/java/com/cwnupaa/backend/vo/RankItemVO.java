package com.cwnupaa.backend.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.Map;

public class RankItemVO {
    private Integer rank;
    private Long itemId;
    private String name;
    private String members;
    private List<Long> memberIds;
    private List<MemberInfo> memberInfos;
    private String avatar;

    private String organization;
    private Boolean isStar;
    private Boolean isGirl;

    private String group;

    private String medal;

    private Integer solved;
    private Long penalty;
    private Long penaltyMillis;
    private Integer totalScore;

    @JsonIgnore
    private boolean hasSubmission;

    private Map<String, ProblemStatus> problemStatus;

    public Integer getRank() {
        return rank;
    }

    public void setRank(Integer rank) {
        this.rank = rank;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMembers() {
        return members;
    }

    public void setMembers(String members) {
        this.members = members;
    }

    public List<Long> getMemberIds() {
        return memberIds;
    }

    public void setMemberIds(List<Long> memberIds) {
        this.memberIds = memberIds;
    }

    public List<MemberInfo> getMemberInfos() {
        return memberInfos;
    }

    public void setMemberInfos(List<MemberInfo> memberInfos) {
        this.memberInfos = memberInfos;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public Boolean getIsStar() {
        return isStar;
    }

    public void setIsStar(Boolean isStar) {
        this.isStar = isStar;
    }

    public Boolean getIsGirl() {
        return isGirl;
    }

    public void setIsGirl(Boolean isGirl) {
        this.isGirl = isGirl;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getMedal() {
        return medal;
    }

    public void setMedal(String medal) {
        this.medal = medal;
    }

    public Integer getSolved() {
        return solved;
    }

    public void setSolved(Integer solved) {
        this.solved = solved;
    }

    public Long getPenalty() {
        return penalty;
    }

    public void setPenalty(Long penalty) {
        this.penalty = penalty;
    }

    public Long getPenaltyMillis() {
        return penaltyMillis;
    }

    public void setPenaltyMillis(Long penaltyMillis) {
        this.penaltyMillis = penaltyMillis;
    }

    public Integer getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
    }

    public boolean getHasSubmission() {
        return hasSubmission;
    }

    public void setHasSubmission(boolean hasSubmission) {
        this.hasSubmission = hasSubmission;
    }

    public Map<String, ProblemStatus> getProblemStatus() {
        return problemStatus;
    }

    public void setProblemStatus(Map<String, ProblemStatus> problemStatus) {
        this.problemStatus = problemStatus;
    }

    public static class ProblemStatus {
        private String status;
        private Integer score;
        private Long time;
        private Integer tryCount;
        private Boolean isFirstAc;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Integer getScore() {
            return score;
        }

        public void setScore(Integer score) {
            this.score = score;
        }

        public Long getTime() {
            return time;
        }

        public void setTime(Long time) {
            this.time = time;
        }

        public Integer getTryCount() {
            return tryCount;
        }

        public void setTryCount(Integer tryCount) {
            this.tryCount = tryCount;
        }

        public Boolean getIsFirstAc() {
            return isFirstAc;
        }

        public void setIsFirstAc(Boolean isFirstAc) {
            this.isFirstAc = isFirstAc;
        }
    }

    public static class MemberInfo {
        private String name;
        private String numberId;

        public MemberInfo() {}
        public MemberInfo(String name, String numberId) {
            this.name = name;
            this.numberId = numberId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getNumberId() {
            return numberId;
        }

        public void setNumberId(String numberId) {
            this.numberId = numberId;
        }
    }
}
