package com.cwnupaa.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cwnupaa.backend.entity.SignupTeam;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SignupTeamMapper extends BaseMapper<SignupTeam> {
    @Select("SELECT COUNT(*) FROM signup_team WHERE event_id = #{eventId} AND status = 'approved'")
    Long countApproved(@Param("eventId") Long eventId);

    @Select("<script>" +
            "SELECT DISTINCT s.* FROM signup_team s " +
            "<if test='keyword != null and keyword != \"\"'> " +
            "  LEFT JOIN signup_team_detail std ON s.signup_id = std.signup_team_id " +
            "  LEFT JOIN users member ON std.user_id = member.user_id " +
            "</if>" +
            "WHERE s.event_id = #{eventId} " +
            "<if test='status != null and status != \"\" and status != \"all\"'> AND s.status = #{status} </if>" +
            "<if test='teamType != null and teamType != \"\" and teamType != \"all\"'>" +
            "  <choose>" +
            "    <when test='teamType == \"official\"'> AND (s.type IN ('official', 'girl') OR s.type IS NULL OR s.type = '') </when>" +
            "    <otherwise> AND s.type = #{teamType} </otherwise>" +
            "  </choose>" +
            "</if>" +
            "<if test='keyword != null and keyword != \"\"'> " +
            "AND (s.team_name LIKE CONCAT('%', #{keyword}, '%') " +
            "OR member.real_name LIKE CONCAT('%', #{keyword}, '%') " +
            "OR member.number_id LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if>" +
            "ORDER BY s.signup_time DESC, s.signup_id DESC" +
            "</script>")
    Page<SignupTeam> selectSignupPage(Page<SignupTeam> page, @Param("eventId") Long eventId, @Param("status") String status, @Param("teamType") String teamType, @Param("keyword") String keyword);

    @Select("<script>" +
            "SELECT DISTINCT s.* FROM signup_team s " +
            "<if test='keyword != null and keyword != \"\"'> " +
            "  LEFT JOIN signup_team_detail std ON s.signup_id = std.signup_team_id " +
            "  LEFT JOIN users member ON std.user_id = member.user_id " +
            "</if>" +
            "WHERE s.event_id = #{eventId} " +
            "<if test='status != null and status != \"\" and status != \"all\"'> AND s.status = #{status} </if>" +
            "<if test='teamType != null and teamType != \"\" and teamType != \"all\"'>" +
            "  <choose>" +
            "    <when test='teamType == \"official\"'> AND (s.type IN ('official', 'girl') OR s.type IS NULL OR s.type = '') </when>" +
            "    <otherwise> AND s.type = #{teamType} </otherwise>" +
            "  </choose>" +
            "</if>" +
            "<if test='keyword != null and keyword != \"\"'> " +
            "AND (s.team_name LIKE CONCAT('%', #{keyword}, '%') " +
            "OR member.real_name LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if>" +
            "ORDER BY s.signup_time DESC" +
            "</script>")
    Page<SignupTeam> selectPublicSignupPage(Page<SignupTeam> page, @Param("eventId") Long eventId, @Param("status") String status, @Param("teamType") String teamType, @Param("keyword") String keyword);

    @Select("SELECT COUNT(*) FROM signup_team s JOIN signup_team_detail d ON s.signup_id = d.signup_team_id WHERE s.event_id = #{eventId} AND d.user_id = #{userId} AND s.status IN ('approved', 'pending')")
    Long countActiveByMember(@Param("eventId") Long eventId, @Param("userId") Long userId);

    // A reactivated older signup must take precedence over later cancelled history.
    @Select("SELECT s.* FROM signup_team s JOIN signup_team_detail d ON s.signup_id = d.signup_team_id WHERE s.event_id = #{eventId} AND d.user_id = #{userId} ORDER BY CASE WHEN s.status IN ('approved', 'pending') THEN 0 ELSE 1 END, s.signup_time DESC, s.signup_id DESC LIMIT 1")
    SignupTeam selectLatestByMember(@Param("eventId") Long eventId, @Param("userId") Long userId);

    @Select("<script>" +
            "SELECT DISTINCT s.* FROM signup_team s JOIN signup_team_detail d ON s.signup_id = d.signup_team_id " +
            "WHERE s.event_id = #{eventId} AND d.user_id IN " +
            "<foreach collection='userIds' item='userId' open='(' separator=',' close=')'>#{userId}</foreach>" +
            " AND s.status IN ('approved', 'pending')" +
            "</script>")
    List<SignupTeam> selectActiveByMembers(@Param("eventId") Long eventId, @Param("userIds") List<Long> userIds);
}
