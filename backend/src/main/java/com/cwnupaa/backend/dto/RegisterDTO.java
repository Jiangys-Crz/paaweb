package com.cwnupaa.backend.dto;

import com.cwnupaa.backend.utils.TrimStringDeserializer;
import com.cwnupaa.backend.utils.UserAccountRules;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。
 *
 * <p>各字段的 {@link Size} 上限与 users 表列宽保持一致（user_account 20、user_name 50、
 * email 100、phone 20、edu_bg 20、real_name 50、number_id 50、class_name 50、
 * grade_year 10）。越过数据库列宽会抛 22001（value too long），过去只能给用户
 * 回一句“数据保存失败”；这里提前拦截并指明具体字段。</p>
 */
public class RegisterDTO {
    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = UserAccountRules.REGEXP, message = UserAccountRules.MESSAGE)
    @Size(max = 20, message = "账号最多20个字符")
    @JsonDeserialize(using = TrimStringDeserializer.class)
    private String userAccount;

    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^[a-fA-F0-9]{32}$", message = "密码格式错误")
    private String password;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称最多50个字符")
    @JsonDeserialize(using = TrimStringDeserializer.class)
    private String userName;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱最多100个字符")
    @JsonDeserialize(using = TrimStringDeserializer.class)
    private String email;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @JsonDeserialize(using = TrimStringDeserializer.class)
    private String phone;

    @NotBlank(message = "身份类型不能为空")
    @Size(max = 20, message = "身份类型取值不合法")
    @Pattern(regexp = "^(undergraduate|postgraduate|department|off-campus)$", message = "身份类型不合法")
    @JsonDeserialize(using = TrimStringDeserializer.class)
    private String eduBg;

    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 50, message = "真实姓名最多50个字符")
    @JsonDeserialize(using = TrimStringDeserializer.class)
    private String realName;

    @NotBlank(message = "学号/工号不能为空")
    @Size(max = 50, message = "学号/工号最多50个字符")
    @JsonDeserialize(using = TrimStringDeserializer.class)
    private String numberId;

    private Integer gender;

    @NotNull(message = "请选择学校/单位")
    private Long universityId;

    private Long schoolId;
    private Long majorId;

    @NotBlank(message = "班级不能为空")
    @Pattern(regexp = "^\\d+$", message = "班级必须为纯数字")
    @Size(max = 50, message = "班级最多50个字符")
    @JsonDeserialize(using = TrimStringDeserializer.class)
    private String className;

    @NotBlank(message = "年级不能为空")
    @Pattern(regexp = "^\\d{4}$", message = "年级/年份格式必须为4位数字，如：2024")
    @Size(max = 10, message = "年级/年份最多10个字符")
    @JsonDeserialize(using = TrimStringDeserializer.class)
    private String gradeYear;

    @NotBlank(message = "验证码不能为空")
    @JsonDeserialize(using = TrimStringDeserializer.class)
    private String verifyCode;

    public String getUserAccount() {
        return userAccount;
    }

    public void setUserAccount(String userAccount) {
        this.userAccount = userAccount;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
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

    public String getEduBg() {
        return eduBg;
    }

    public void setEduBg(String eduBg) {
        this.eduBg = eduBg;
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

    public Long getUniversityId() {
        return universityId;
    }

    public void setUniversityId(Long universityId) {
        this.universityId = universityId;
    }

    public Long getSchoolId() {
        return schoolId;
    }

    public void setSchoolId(Long schoolId) {
        this.schoolId = schoolId;
    }

    public Long getMajorId() {
        return majorId;
    }

    public void setMajorId(Long majorId) {
        this.majorId = majorId;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getGradeYear() {
        return gradeYear;
    }

    public void setGradeYear(String gradeYear) {
        this.gradeYear = gradeYear;
    }

    public String getVerifyCode() {
        return verifyCode;
    }

    public void setVerifyCode(String verifyCode) {
        this.verifyCode = verifyCode;
    }
}
