package com.cwnupaa.backend.vo;

public class ExternalUserInfoVO {
    private Long userId;
    private String userAccount;
    private String userName;
    private String realName;
    private String numberId;
    private String email;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserAccount() { return userAccount; }
    public void setUserAccount(String userAccount) { this.userAccount = userAccount; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getNumberId() { return numberId; }
    public void setNumberId(String numberId) { this.numberId = numberId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}