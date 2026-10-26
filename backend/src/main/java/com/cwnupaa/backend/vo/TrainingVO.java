package com.cwnupaa.backend.vo;

import com.cwnupaa.backend.entity.FileSystem;
import com.cwnupaa.backend.entity.Training;

import java.util.List;

public class TrainingVO extends Training {
    private String termName;
    private String teacherName;
    private String teacherAccount;
    private List<TrainingTeacherVO> teachers;
    private Long signupCount;
    private String state; // 业务状态
    private List<FileSystem> attachments;

    public String getTermName() {
        return termName;
    }

    public void setTermName(String termName) {
        this.termName = termName;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }

    public Long getSignupCount() {
        return signupCount;
    }

    public void setSignupCount(Long signupCount) {
        this.signupCount = signupCount;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getTeacherAccount() {
        return teacherAccount;
    }

    public void setTeacherAccount(String teacherAccount) {
        this.teacherAccount = teacherAccount;
    }

    public List<TrainingTeacherVO> getTeachers() {
        return teachers;
    }

    public void setTeachers(List<TrainingTeacherVO> teachers) {
        this.teachers = teachers;
    }

    public List<FileSystem> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<FileSystem> attachments) {
        this.attachments = attachments;
    }
}
