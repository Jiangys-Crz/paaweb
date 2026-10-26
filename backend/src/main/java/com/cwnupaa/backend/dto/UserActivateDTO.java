package com.cwnupaa.backend.dto;

import com.cwnupaa.backend.utils.UserAccountRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UserActivateDTO {
    private Long userId;
    private String nickName; // user_name
    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = UserAccountRules.REGEXP, message = UserAccountRules.MESSAGE)
    private String userAccount; // login account
    @NotBlank @jakarta.validation.constraints.Email @jakarta.validation.constraints.Size(max = 100)
    private String email;
    @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
    @NotBlank @Pattern(regexp = "^[a-fA-F0-9]{32}$", message = "密码格式错误")
    private String password; // new password
    @NotBlank
    private String verifyCode;
}
