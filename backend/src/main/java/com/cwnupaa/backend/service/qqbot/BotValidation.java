package com.cwnupaa.backend.service.qqbot;

import java.net.URI;
import java.util.*;

/** Never interpret user text as a URL, OneBot action, SQL or CQ markup. */
public final class BotValidation {
    private BotValidation() {}
    public static String group(String id) {
        if (id == null || !id.matches("[1-9][0-9]{4,14}")) throw new IllegalArgumentException("群号需为5–15位正整数");
        return id;
    }
    public static List<String> groups(List<String> ids) {
        if (ids == null) return new ArrayList<>();
        if (ids.size() > 100) throw new IllegalArgumentException("最多配置100个群");
        return new ArrayList<>(new LinkedHashSet<>(ids.stream().map(BotValidation::group).toList()));
    }
    public static URI endpoint(String value, boolean socket) {
        try {
            URI uri = URI.create(value);
            Set<String> schemes = socket ? Set.of("ws", "wss") : Set.of("https", "http");
            if (!schemes.contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getFragment() != null || uri.getQuery() != null || value.length() > 1024) throw new IllegalArgumentException();
            return uri;
        } catch (Exception e) { throw new IllegalArgumentException(socket ? "请填写有效的 ws/wss 地址，凭据请填入独立密钥字段" : "请填写有效的 http/https 服务地址"); }
    }
    public static String secret(String value, int max) {
        if (value != null && (value.length() > max || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0))
            throw new IllegalArgumentException("密钥长度或格式不合法");
        return value;
    }
    public static void publicImage(String value) {
        try {
            URI uri = URI.create(value);
            if (value.length() > 2048 || !"https".equals(uri.getScheme()) || uri.getUserInfo() != null
                    || uri.getFragment() != null || uri.getHost() == null || !com.cwnupaa.backend.utils.DeploymentUrls.imageHosts().contains(uri.getHost().toLowerCase(Locale.ROOT))
                    || uri.getPort() != -1 && uri.getPort() != 443) throw new IllegalArgumentException();
        } catch (Exception e) { throw new IllegalArgumentException("图片请使用协会官网公开的 HTTPS 图片链接，其他图片请先上传官网"); }
    }
    public static void range(int v, int min, int max, String name) {
        if (v < min || v > max) throw new IllegalArgumentException(name + "需在" + min + "–" + max + "之间");
    }
}
