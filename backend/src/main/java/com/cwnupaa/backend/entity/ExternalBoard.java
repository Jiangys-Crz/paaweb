package com.cwnupaa.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("external_board")
public class ExternalBoard {
    @TableId(type = IdType.AUTO)
    private Long boardId;
    private Long eventId;
    private String name;
    @TableField("is_external_open")
    private Boolean externalOpen;
    @TableField("is_unfrozen")
    private Boolean unfrozen;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime actualStartTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime actualEndTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime freezeTime;
    private String medalConfig;
    private String organizationDisplay;
    private Boolean resolverEnabled;
    private Boolean resolverSingleStep;
    private Double resolverSpeed;
    private String resolverSettings;
    private String resolverPasswordHash;
}
