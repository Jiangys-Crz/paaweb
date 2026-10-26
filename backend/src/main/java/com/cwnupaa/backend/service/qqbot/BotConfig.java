package com.cwnupaa.backend.service.qqbot;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class BotConfig {
    private boolean enabled;
    private String wsUrl = "ws://127.0.0.1:3001";
    private String accessToken;
    private boolean accessTokenConfigured;
    private String hydroBaseUrl = com.cwnupaa.backend.utils.DeploymentUrls.hydro();
    private String hydroApiKey;
    private boolean hydroApiKeyConfigured;
    private int reconnectSeconds = 10;
    private int typingMillisPerChar = 60;
    private int minReplyDelayMillis = 600;
    private int maxReplyDelayMillis = 12000;
    private List<String> queryGroups = new ArrayList<>();
    private List<String> pushGroups = new ArrayList<>();
    private boolean pushOffline;
    private boolean pushOnline;
    private boolean pushTraining;
    private boolean pushBoard;

    public boolean allowsPush(String category) {
        return switch (category) {
            case "offline" -> pushOffline;
            case "online" -> pushOnline;
            case "training" -> pushTraining;
            case "board" -> pushBoard;
            default -> false;
        };
    }
}
