package com.cwnupaa.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class ExternalSendCodeDTO {
    @NotBlank(message = "AppKey不能为空")
    private String appKey;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    public String getAppKey() { return appKey; }
    public void setAppKey(String appKey) { this.appKey = appKey; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}