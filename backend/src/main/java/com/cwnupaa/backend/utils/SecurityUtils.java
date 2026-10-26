package com.cwnupaa.backend.utils;

import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.vo.UserProfileVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Set;

public final class SecurityUtils {
    private static final int DEFAULT_MAX_PAGE_SIZE = 100;
    private static final Set<String> ADMIN_ROLES = Set.of("supper_admin", "super_admin", "stu_admin", "department", "school_admin");
    private static final Set<String> ALLOWED_ROLES = Set.of("student", "school_admin", "department", "stu_admin", "super_admin", "supper_admin");
    private static final SecureRandom ACCOUNT_RANDOM = new SecureRandom();
    private static final char[] ACCOUNT_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private SecurityUtils() {
    }

    public static UserProfileVO currentUser(HttpServletRequest request) {
        Object attrUser = request.getAttribute("user");
        if (attrUser instanceof UserProfileVO user) return user;

        HttpSession session = request.getSession(false);
        Object sessionUser = session == null ? null : session.getAttribute("user");
        return sessionUser instanceof UserProfileVO user ? user : null;
    }

    public static Long currentUserId(HttpServletRequest request) {
        UserProfileVO user = currentUser(request);
        return user == null ? null : user.getUserId();
    }

    public static boolean isAdmin(UserProfileVO user) {
        return user != null && ADMIN_ROLES.contains(user.getRole());
    }

    public static boolean isAllowedRole(String role) { return role != null && ALLOWED_ROLES.contains(role); }

    public static boolean isRoot(UserProfileVO user) {
        return user != null && "admin".equals(user.getUserAccount())
                && ("supper_admin".equals(user.getRole()) || "super_admin".equals(user.getRole()));
    }

    public static boolean hasPermission(UserProfileVO user, String permission) {
        return user != null && "normal".equals(user.getAccountStatus()) && isAdmin(user)
                && (isRoot(user) || user.getPermissions() != null && user.getPermissions().contains(permission));
    }

    public static int roleRank(String role) {
        if ("supper_admin".equals(role) || "super_admin".equals(role)) return 4;
        if ("stu_admin".equals(role)) return 3;
        if ("department".equals(role) || "school_admin".equals(role)) return 2;
        return 0;
    }

    /** 后台敏感写入统一目标约束；非内置管理员不能操作自身、同级或更高等级。 */
    public static boolean canManageTarget(UserProfileVO operator, com.cwnupaa.backend.entity.Users target) {
        if (target == null || !hasPermission(operator, "manage_user")) return false;
        if (isRoot(operator)) return true;
        if ("admin".equals(target.getUserAccount()) || operator.getUserId().equals(target.getUserId())) return false;
        return roleRank(operator.getRole()) > roleRank(target.getRole());
    }

    public static com.cwnupaa.backend.vo.UserSearchVO publicSearchUser(com.cwnupaa.backend.entity.Users user) {
        var result = new com.cwnupaa.backend.vo.UserSearchVO();
        result.setUserId(user.getUserId());
        result.setUserName(user.getUserName());
        result.setAvatarFileId(user.getAvatarFileId());
        if (user.getAvatarFileId() != null) result.setAvatarUrl("/api/file/image/" + user.getAvatarFileId());
        return result;
    }

    public static boolean isSelfOrAdmin(HttpServletRequest request, Long targetUserId) {
        UserProfileVO user = currentUser(request);
        return user != null && (user.getUserId().equals(targetUserId) || isAdmin(user));
    }

    public static Result<String> requireLogin(HttpServletRequest request) {
        return currentUser(request) == null ? Result.error("未登录或会话已失效") : null;
    }

    public static int clampPageSize(Integer size) {
        if (size == null || size < 1) return 10;
        return Math.min(size, DEFAULT_MAX_PAGE_SIZE);
    }

    public static int normalizePage(Integer page) {
        if (page == null || page < 1) return 1;
        return page;
    }

    public static String trimToNull(String value, int maxLength) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            return trimmed.substring(0, maxLength);
        }
        return trimmed;
    }

    private static final char[] PASSWORD_ALPHABET =
            "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    /**
     * 生成随机初始密码（安全随机，默认 10 位，不含易混淆字符）。
     */
    public static String randomPassword(int length) {
        java.security.SecureRandom random = new java.security.SecureRandom();
        int len = Math.max(6, Math.min(length, 32));
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(PASSWORD_ALPHABET[random.nextInt(PASSWORD_ALPHABET.length)]);
        }
        return sb.toString();
    }

    public static String randomPassword() {
        return randomPassword(10);
    }

    /**
     * 为待激活的导入用户生成内部占位账号。
     *
     * 学号只在“学校 + 学号”的范围内唯一，不能继续直接占用全局唯一的 user_account。
     * 用户完成激活后会设置自己的正式登录账号。
     */
    public static String pendingActivationAccount() {
        StringBuilder account = new StringBuilder(10);
        account.append('p');
        for (int i = 1; i < 10; i++) {
            account.append(ACCOUNT_ALPHABET[ACCOUNT_RANDOM.nextInt(ACCOUNT_ALPHABET.length)]);
        }
        return account.toString();
    }
}
