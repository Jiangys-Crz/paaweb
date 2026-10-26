package com.cwnupaa.backend.vo;

public class CertificateImportMemberVO {
    private String name;
    private String studentId;
    private String school; // 成员/获奖人所属学校（大学）名称

    public CertificateImportMemberVO() { }

    public CertificateImportMemberVO(String name, String studentId) {
        this.name = name;
        this.studentId = studentId;
    }

    public CertificateImportMemberVO(String name, String studentId, String school) {
        this.name = name;
        this.studentId = studentId;
        this.school = school;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getSchool() { return school; }
    public void setSchool(String school) { this.school = school; }
}
