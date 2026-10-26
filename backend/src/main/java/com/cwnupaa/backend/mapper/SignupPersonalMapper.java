package com.cwnupaa.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cwnupaa.backend.entity.SignupPersonal;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SignupPersonalMapper extends BaseMapper<SignupPersonal> {
    @Select("SELECT COUNT(*) FROM signup_personal WHERE event_id = #{eventId} AND status = 'approved'")
    Long countApproved(@Param("eventId") Long eventId);

    @Select("<script>" +
            "SELECT s.* FROM signup_personal s " +
            "<if test='keyword != null and keyword != \"\"'> LEFT JOIN users u ON s.user_id = u.user_id </if>" +
            "WHERE s.event_id = #{eventId} " +
            "<if test='status != null and status != \"\" and status != \"all\"'> AND s.status = #{status} </if>" +
            "<if test='teamType != null and teamType != \"\" and teamType != \"all\"'>" +
            "  <choose>" +
            "    <when test='teamType == \"official\"'> AND (s.type IN ('official', 'girl') OR s.type IS NULL OR s.type = '') </when>" +
            "    <otherwise> AND s.type = #{teamType} </otherwise>" +
            "  </choose>" +
            "</if>" +
            "<if test='keyword != null and keyword != \"\"'> AND (u.real_name LIKE CONCAT('%', #{keyword}, '%') OR u.number_id LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "ORDER BY s.signup_time DESC" +
            "</script>")
    Page<SignupPersonal> selectSignupPage(Page<SignupPersonal> page, @Param("eventId") Long eventId, @Param("status") String status, @Param("teamType") String teamType, @Param("keyword") String keyword);

    @Select("<script>" +
            "SELECT s.* FROM signup_personal s " +
            "<if test='keyword != null and keyword != \"\"'> LEFT JOIN users u ON s.user_id = u.user_id </if>" +
            "WHERE s.event_id = #{eventId} " +
            "<if test='status != null and status != \"\" and status != \"all\"'> AND s.status = #{status} </if>" +
            "<if test='teamType != null and teamType != \"\" and teamType != \"all\"'>" +
            "  <choose>" +
            "    <when test='teamType == \"official\"'> AND (s.type IN ('official', 'girl') OR s.type IS NULL OR s.type = '') </when>" +
            "    <otherwise> AND s.type = #{teamType} </otherwise>" +
            "  </choose>" +
            "</if>" +
            "<if test='keyword != null and keyword != \"\"'> AND (u.real_name LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "ORDER BY s.signup_time DESC" +
            "</script>")
    Page<SignupPersonal> selectPublicSignupPage(Page<SignupPersonal> page, @Param("eventId") Long eventId, @Param("status") String status, @Param("teamType") String teamType, @Param("keyword") String keyword);
}
