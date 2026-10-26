package com.cwnupaa.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 通过“学校（大学）+ 学号 + 初始密码”激活账号，并补充注册流程中的缺失信息。
 */
public class UserActivateByIdentityDTO extends UserActivateDTO {
    @NotNull(message = "请选择学校")
    private Long universityId;

    @NotBlank(message = "学号不能为空")
    private String numberId;

    @NotBlank(message = "初始密码不能为空")
    private String initialPassword;

    private Integer gender;

    public Long getUniversityId() { return universityId; }
    public void setUniversityId(Long universityId) { this.universityId = universityId; }
    public String getNumberId() { return numberId; }
    public void setNumberId(String numberId) { this.numberId = numberId; }
    public String getInitialPassword() { return initialPassword; }
    public void setInitialPassword(String initialPassword) { this.initialPassword = initialPassword; }
    public Integer getGender() { return gender; }
    public void setGender(Integer gender) { this.gender = gender; }
}
