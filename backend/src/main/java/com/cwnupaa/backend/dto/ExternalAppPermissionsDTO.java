package com.cwnupaa.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ExternalAppPermissionsDTO(
        @NotNull(message = "密钥ID不能为空") @Positive(message = "密钥ID无效") Long appId,
        @NotNull(message = "请设置外部登录权限") Boolean externalLoginEnabled,
        @NotNull(message = "请设置外榜权限") Boolean externalBoardEnabled) {
}
