package com.cwnupaa.backend.utils;

/**
 * 表单文本的统一清洗。
 *
 * <p>注册/激活等表单里，用户常常会带上前后空格（尤其是从 Excel、聊天记录里复制粘贴），
 * 这些空格既会让 {@code ^\d{4}$} 这类格式校验莫名失败，也可能把脏数据写进数据库，
 * 甚至把长度顶到列宽上限。所有入口统一走这里处理。</p>
 */
public final class TextUtils {

    private TextUtils() {
    }

    /** 去掉前后空白：含半角/全角空格、不换行空格、制表符与换行。 */
    public static String trim(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replace('\u3000', ' ')   // 全角空格
                .replace('\u00A0', ' ')   // 不换行空格
                .replace('\uFEFF', ' ')   // BOM（复制粘贴常见）
                .trim();
    }
}
