package com.cwnupaa.backend.dto;

/**
 * 注册分步校验请求。step=0 校验账号、邮箱和手机号，step=1 校验学校+学号。
 */
public class RegisterAvailabilityDTO {
    private Integer step;
    private String userAccount;
    private String email;
    private String phone;
    private String numberId;
    private Long universityId;
    private Long majorId;

    public Integer getStep() { return step; }
    public void setStep(Integer step) { this.step = step; }
    public String getUserAccount() { return userAccount; }
    public void setUserAccount(String userAccount) { this.userAccount = userAccount; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getNumberId() { return numberId; }
    public void setNumberId(String numberId) { this.numberId = numberId; }
    public Long getUniversityId() { return universityId; }
    public void setUniversityId(Long universityId) { this.universityId = universityId; }
    public Long getMajorId() { return majorId; }
    public void setMajorId(Long majorId) { this.majorId = majorId; }
}
