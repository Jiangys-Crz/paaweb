package com.cwnupaa.backend.vo;

import com.cwnupaa.backend.entity.CertificateType;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class CertificateGrantVO extends CertificateType {
    private String levelName;
    private String levelType;
    private String termName;
    private String templateImageUrl;
    private Long certificateDownloadId;
    private String certificateNo;
    private String awardName;
    private String teamName;
    private String accessToken;

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

    public String getTermName() {
        return termName;
    }

    public void setTermName(String termName) {
        this.termName = termName;
    }

    public String getTemplateImageUrl() {
        return templateImageUrl;
    }

    public void setTemplateImageUrl(String templateImageUrl) {
        this.templateImageUrl = templateImageUrl;
    }

    @JsonIgnore
    public Long getCertificateDownloadId() {
        return certificateDownloadId;
    }

    public void setCertificateDownloadId(Long certificateDownloadId) {
        this.certificateDownloadId = certificateDownloadId;
    }

    public String getCertificateNo() { return certificateNo; }
    public void setCertificateNo(String certificateNo) { this.certificateNo = certificateNo; }
    public String getAwardName() { return awardName; }
    public void setAwardName(String awardName) { this.awardName = awardName; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    /**
     * Rating participation is an administrative template setting and must not be
     * exposed by certificate APIs used by recipients.
     */
    @Override
    @JsonIgnore
    public Boolean getRatingEnabled() {
        return super.getRatingEnabled();
    }
}
