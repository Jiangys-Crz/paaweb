package com.cwnupaa.backend.service.qqbot;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class BotTask {
    private String id;
    private String revision;
    private String title;
    private String groupId;
    private String content;
    private List<String> images = new ArrayList<>();
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dueAt;
    private String recurrence = "ONCE";
    private Integer intervalMinutes;
    private boolean enabled = true;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastSentAt;
    private String lastError;
}
