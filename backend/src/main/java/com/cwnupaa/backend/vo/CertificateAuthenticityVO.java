package com.cwnupaa.backend.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CertificateAuthenticityVO {
    private boolean found;
    private boolean valid;
    private String message;
    private String certificateNo;
    private String certificateName;
    private String certificateKind;
    private String levelScope;
    private String teamName;
    private String awardName;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate issueDate;
    private List<CertificateImportMemberVO> members = new ArrayList<>();

    public boolean isFound() { return found; }
    public void setFound(boolean found) { this.found = found; }
    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getCertificateNo() { return certificateNo; }
    public void setCertificateNo(String certificateNo) { this.certificateNo = certificateNo; }
    public String getCertificateName() { return certificateName; }
    public void setCertificateName(String certificateName) { this.certificateName = certificateName; }
    public String getCertificateKind() { return certificateKind; }
    public void setCertificateKind(String certificateKind) { this.certificateKind = certificateKind; }
    public String getLevelScope() { return levelScope; }
    public void setLevelScope(String levelScope) { this.levelScope = levelScope; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public String getAwardName() { return awardName; }
    public void setAwardName(String awardName) { this.awardName = awardName; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
    public List<CertificateImportMemberVO> getMembers() { return members; }
    public void setMembers(List<CertificateImportMemberVO> members) { this.members = members; }
}
