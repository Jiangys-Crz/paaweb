package com.cwnupaa.backend.service.qqbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.BadSqlGrammarException;

@Service
public class BotConfigService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private volatile BotConfig cached;
    private volatile long expires;
    public BotConfigService(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public BotConfig get() {
        if (cached != null && System.currentTimeMillis() < expires) return copy(cached);
        synchronized (this) {
            try {
                var rows = jdbc.queryForList("SELECT payload FROM qq_bot_config WHERE id=1", String.class);
                cached = rows.isEmpty() ? new BotConfig() : json.readValue(rows.getFirst(), BotConfig.class);
            } catch (BadSqlGrammarException e) {
                // Remain disabled when the optional bot storage is unavailable.
                cached = new BotConfig();
            } catch (Exception e) { throw new IllegalStateException("QQ机器人配置读取失败", e); }
            expires = System.currentTimeMillis() + 5000;
            return copy(cached);
        }
    }
    public BotConfig publicConfig() {
        BotConfig c = get();
        c.setAccessTokenConfigured(c.getAccessToken() != null && !c.getAccessToken().isBlank());
        c.setHydroApiKeyConfigured(c.getHydroApiKey() != null && !c.getHydroApiKey().isBlank());
        c.setAccessToken(null); c.setHydroApiKey(null);
        return c;
    }
    public synchronized void save(BotConfig c, Long operatorId) {
        BotConfig old = get();
        if (c.getAccessToken() == null || c.getAccessToken().isBlank()) c.setAccessToken(old.getAccessToken());
        if (c.getHydroApiKey() == null || c.getHydroApiKey().isBlank()) c.setHydroApiKey(old.getHydroApiKey());
        validate(c);
        try {
            jdbc.update("INSERT INTO qq_bot_config(id,payload,operator_id) VALUES(1,?,?) "
                    + "ON CONFLICT(id) DO UPDATE SET payload=EXCLUDED.payload,operator_id=EXCLUDED.operator_id,updated_at=CURRENT_TIMESTAMP",
                    json.writeValueAsString(c), operatorId);
            cached = copy(c); expires = System.currentTimeMillis() + 5000;
        } catch (Exception e) { throw new IllegalStateException("QQ机器人配置保存失败，请确认已执行数据库迁移", e); }
    }
    public static void validate(BotConfig c) {
        BotValidation.endpoint(c.getWsUrl(), true);
        if (c.getHydroBaseUrl() != null && !c.getHydroBaseUrl().isBlank()) {
            BotValidation.endpoint(c.getHydroBaseUrl(), false);
            c.setHydroBaseUrl(c.getHydroBaseUrl().replaceAll("/+$", ""));
        } else if (c.getHydroApiKey() != null && !c.getHydroApiKey().isBlank()) {
            throw new IllegalArgumentException("配置 Hydro API Key 时需要填写 Hydro 服务地址");
        }
        BotValidation.secret(c.getAccessToken(), 512); BotValidation.secret(c.getHydroApiKey(), 256);
        if (c.isEnabled() && (c.getAccessToken() == null || c.getAccessToken().isBlank()))
            throw new IllegalArgumentException("启用前请配置 OneBot Access Token");
        if (c.getHydroApiKey() != null && !c.getHydroApiKey().isBlank() && c.getHydroApiKey().length() < 32)
            throw new IllegalArgumentException("Hydro API-key至少32位");
        BotValidation.range(c.getReconnectSeconds(),1,3600,"重连间隔");
        BotValidation.range(c.getTypingMillisPerChar(),0,1000,"每字延迟");
        BotValidation.range(c.getMinReplyDelayMillis(),0,60000,"最小延迟");
        BotValidation.range(c.getMaxReplyDelayMillis(),c.getMinReplyDelayMillis(),60000,"最大延迟");
        c.setQueryGroups(BotValidation.groups(c.getQueryGroups()));
        c.setPushGroups(BotValidation.groups(c.getPushGroups()));
    }
    private BotConfig copy(BotConfig c) { return json.convertValue(c, BotConfig.class); }
}
