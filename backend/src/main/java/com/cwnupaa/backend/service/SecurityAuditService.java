package com.cwnupaa.backend.service;

import com.cwnupaa.backend.entity.SecurityAuditRecord;
import com.cwnupaa.backend.mapper.SecurityAuditMapper;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;

/** 仅保存字段名称和操作元数据，不保存邮箱、密码、验证码等字段值。 */
@Service
public class SecurityAuditService {
    private final SecurityAuditMapper mapper;
    public SecurityAuditService(SecurityAuditMapper mapper) { this.mapper = mapper; }

    public void record(Long actorId, Long targetUserId, String action, Collection<String> fields) {
        SecurityAuditRecord record = new SecurityAuditRecord();
        record.setActorId(actorId);
        record.setTargetUserId(targetUserId);
        record.setAction(action);
        record.setChangedFields(String.join(",", fields));
        record.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        mapper.insert(record);
    }
}
