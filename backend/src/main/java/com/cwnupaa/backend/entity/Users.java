package com.cwnupaa.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("users")
public class Users {
    @TableId(type = IdType.AUTO)
    private Long userId;
    private String userAccount;
    private String userName;

    private String password;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private Long authVersion = 0L;

    public Long getAuthVersion() { return authVersion; }
    public void setAuthVersion(Long authVersion) { this.authVersion = authVersion; }
    private String email;
    private String phone;
    private String role;
    private String accountStatus;
    private LocalDateTime banUntil;
    private String banReason;
    private String preBanStatus;

    public LocalDateTime getBanUntil() { return banUntil; }
    public void setBanUntil(LocalDateTime value) { banUntil = value; }
    public String getBanReason() { return banReason; }
    public void setBanReason(String value) { banReason = value; }
    public String getPreBanStatus() { return preBanStatus; }
    public void setPreBanStatus(String value) { preBanStatus = value; }

    private String introduce;
    private String realName;
    private String numberId;
    private Integer gender;
    private String eduBg;
    private Long majorId;
    private String gradeYear;
    private String className;
    private Long avatarFileId;
    private Long backgroundFileId;
    private Long idPhotoFileId;
    private LocalDateTime idPhotoUpdatedAt;
    private LocalDateTime registerTime;
    private Long studentCardFileId;
    private Integer isEnrolledStu;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserAccount() {
        return userAccount;
    }

    public void setUserAccount(String userAccount) {
        this.userAccount = userAccount;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }

    public String getIntroduce() {
        return introduce;
    }

    public void setIntroduce(String introduce) {
        this.introduce = introduce;
    }

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

    public Integer getGender() {
        return gender;
    }

    public void setGender(Integer gender) {
        this.gender = gender;
    }

    public String getEduBg() {
        return eduBg;
    }

    public void setEduBg(String eduBg) {
        this.eduBg = eduBg;
    }

    public Long getMajorId() {
        return majorId;
    }

    public void setMajorId(Long majorId) {
        this.majorId = majorId;
    }

    public String getGradeYear() {
        return gradeYear;
    }

    public void setGradeYear(String gradeYear) {
        this.gradeYear = gradeYear;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public Long getAvatarFileId() {
        return avatarFileId;
    }

    public void setAvatarFileId(Long avatarFileId) {
        this.avatarFileId = avatarFileId;
    }

    public Long getBackgroundFileId() {
        return backgroundFileId;
    }

    public void setBackgroundFileId(Long backgroundFileId) {
        this.backgroundFileId = backgroundFileId;
    }

    public Long getIdPhotoFileId() {
        return idPhotoFileId;
    }

    public void setIdPhotoFileId(Long idPhotoFileId) {
        this.idPhotoFileId = idPhotoFileId;
    }

    public LocalDateTime getIdPhotoUpdatedAt() {
        return idPhotoUpdatedAt;
    }

    public void setIdPhotoUpdatedAt(LocalDateTime idPhotoUpdatedAt) {
        this.idPhotoUpdatedAt = idPhotoUpdatedAt;
    }

    public LocalDateTime getRegisterTime() {
        return registerTime;
    }

    public void setRegisterTime(LocalDateTime registerTime) {
        this.registerTime = registerTime;
    }

    public Long getStudentCardFileId() {
        return studentCardFileId;
    }

    public void setStudentCardFileId(Long studentCardFileId) {
        this.studentCardFileId = studentCardFileId;
    }

    public Integer getIsEnrolledStu() {
        return isEnrolledStu;
    }

    public void setIsEnrolledStu(Integer isEnrolledStu) {
        this.isEnrolledStu = isEnrolledStu;
    }
}
