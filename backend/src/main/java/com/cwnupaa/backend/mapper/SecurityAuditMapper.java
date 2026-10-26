package com.cwnupaa.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cwnupaa.backend.entity.SecurityAuditRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SecurityAuditMapper extends BaseMapper<SecurityAuditRecord> {}
