package com.cwnupaa.backend.vo;

import java.util.ArrayList;
import java.util.List;

public class CertificateImportRowVO {
    private Integer rowNumber;
    private String teamName;
    private String awardName;
    private String certificateNo;
    private List<CertificateImportMemberVO> members = new ArrayList<>();

    public Integer getRowNumber() { return rowNumber; }
    public void setRowNumber(Integer rowNumber) { this.rowNumber = rowNumber; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public String getAwardName() { return awardName; }
    public void setAwardName(String awardName) { this.awardName = awardName; }
    public String getCertificateNo() { return certificateNo; }
    public void setCertificateNo(String certificateNo) { this.certificateNo = certificateNo; }
    public List<CertificateImportMemberVO> getMembers() { return members; }
    public void setMembers(List<CertificateImportMemberVO> members) { this.members = members; }
}
