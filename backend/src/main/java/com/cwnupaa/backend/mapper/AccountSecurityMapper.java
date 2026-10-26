package com.cwnupaa.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AccountSecurityMapper {
    @Select(value = "UPDATE users SET auth_version = COALESCE(auth_version, 0) + 1 WHERE user_id = #{userId} RETURNING auth_version", affectData = true)
    Long incrementAuthVersion(@Param("userId") Long userId);
}
