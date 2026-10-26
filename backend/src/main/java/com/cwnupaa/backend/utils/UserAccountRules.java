package com.cwnupaa.backend.utils;

import java.util.regex.Pattern;

/**
 * 站内登录账号的统一规则。
 */
public final class UserAccountRules {
    public static final String REGEXP = "^(?=.*[A-Za-z])[A-Za-z0-9]{3,20}$";
    public static final String MESSAGE = "用户名需为3-20个英文字母或数字，且至少包含1个英文字母";

    private static final Pattern PATTERN = Pattern.compile(REGEXP);

    private UserAccountRules() {
    }

    public static boolean isValid(String account) {
        return account != null && PATTERN.matcher(account).matches();
    }
}
