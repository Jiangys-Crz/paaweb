package com.cwnupaa.backend.vo;

import com.cwnupaa.backend.entity.CertificateVerify;

public class CertificateVerifyVO extends CertificateVerify {
    private String levelName;
    private String levelType;
    private String categoryName;
    private String termName;
    private String imageUrl;
    private String userRealName;
    private String userNumberId;
    private String userMajorName;
    private String userClassName;
    private String auditUserRealName;
    
    private String subProjectName;
    private Integer isTeam;
    private String advisor;
    private java.util.List<MemberVO> members;

    public static class MemberVO {
        private String name;
        private String studentId;
        private Integer isCaptain;
        private Integer sortOrder;
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getStudentId() { return studentId; }
        public void setStudentId(String studentId) { this.studentId = studentId; }
        public Integer getIsCaptain() { return isCaptain; }
        public void setIsCaptain(Integer isCaptain) { this.isCaptain = isCaptain; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }

    public String getSubProjectName() { return subProjectName; }
    public void setSubProjectName(String subProjectName) { this.subProjectName = subProjectName; }

    public Integer getIsTeam() { return isTeam; }
    public void setIsTeam(Integer isTeam) { this.isTeam = isTeam; }

    public String getAdvisor() { return advisor; }
    public void setAdvisor(String advisor) { this.advisor = advisor; }

    public java.util.List<MemberVO> getMembers() { return members; }
    public void setMembers(java.util.List<MemberVO> members) { this.members = members; }

    public String getLevelName() {
        return levelName;
    }

    public void setLevelName(String levelName) {
        this.levelName = levelName;
    }

    public String getLevelType() {
        return levelType;
    }

    public void setLevelType(String levelType) {
        this.levelType = levelType;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getTermName() {
        return termName;
    }

    public void setTermName(String termName) {
        this.termName = termName;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getUserRealName() {
        return userRealName;
    }

    public void setUserRealName(String userRealName) {
        this.userRealName = userRealName;
    }

    public String getUserNumberId() {
        return userNumberId;
    }

    public void setUserNumberId(String userNumberId) {
        this.userNumberId = userNumberId;
    }

    public String getUserMajorName() {
        return userMajorName;
    }

    public void setUserMajorName(String userMajorName) {
        this.userMajorName = userMajorName;
    }

    public String getUserClassName() {
        return userClassName;
    }

    public void setUserClassName(String userClassName) {
        this.userClassName = userClassName;
    }

    public String getAuditUserRealName() {
        return auditUserRealName;
    }

    public void setAuditUserRealName(String auditUserRealName) {
        this.auditUserRealName = auditUserRealName;
    }
}