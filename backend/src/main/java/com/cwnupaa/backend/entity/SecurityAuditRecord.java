package com.cwnupaa.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("security_audit_records")
public class SecurityAuditRecord {
    @TableId(type = IdType.AUTO)
    private Long auditId;
    private Long actorId;
    private Long targetUserId;
    private String action;
    private String changedFields;
    private LocalDateTime createdAt;
}
