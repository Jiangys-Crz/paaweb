package com.cwnupaa.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class ExternalLoginDTO {
    @NotBlank(message = "AppKey不能为空")
    private String appKey; // 分配给第三方网站的专属访问密钥

    @NotBlank(message = "登录类型不能为空")
    private String loginType; // "password" 或 "code"

    @NotBlank(message = "账号不能为空")
    private String account; // 密码登录时为：用户名/邮箱；验证码登录时为：邮箱

    @NotBlank(message = "凭证不能为空")
    private String credential; // 密码 (明文) 或 邮箱验证码

    // 请生成对应的 Getter 和 Setter
    public String getAppKey() { return appKey; }
    public void setAppKey(String appKey) { this.appKey = appKey; }
    public String getLoginType() { return loginType; }
    public void setLoginType(String loginType) { this.loginType = loginType; }
    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    public String getCredential() { return credential; }
    public void setCredential(String credential) { this.credential = credential; }
}
