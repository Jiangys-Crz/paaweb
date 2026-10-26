package com.cwnupaa.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cwnupaa.backend.entity.TrainingSignup;
import com.cwnupaa.backend.vo.TrainingSignupVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TrainingSignupMapper extends BaseMapper<TrainingSignup> {

    @Select("SELECT COUNT(*) FROM training_signup WHERE train_id = #{trainId} AND status = 'approved'")
    Long countApproved(Long trainId);
    @Select("<script>" + "SELECT ts.*, u.real_name, u.number_id, u.class_name, m.major_name, s.school_name, uni.university_name " + "FROM training_signup ts " + "LEFT JOIN users u ON ts.user_id = u.user_id " + "LEFT JOIN major m ON u.major_id = m.major_id " + "LEFT JOIN school s ON m.school_id = s.school_id " + "LEFT JOIN university uni ON s.university_id = uni.university_id " + "WHERE ts.train_id = #{trainId} " + "<if test='keyword != null and keyword != \"\"'>" + "AND (u.real_name LIKE CONCAT('%',#{keyword},'%') OR u.number_id LIKE CONCAT('%',#{keyword},'%')) " + "</if>" + "<if test='status != null and status != \"\" and status != \"all\"'>" + "AND ts.status = #{status} " + "</if>" + "ORDER BY ts.create_time DESC" + "</script>")
    Page<TrainingSignupVO> selectPageVo(Page<TrainingSignupVO> page, @Param("trainId") Long trainId, @Param("keyword") String keyword, @Param("status") String status);
    @Select("SELECT ts.*, u.real_name, u.number_id, u.class_name, m.major_name, s.school_name, uni.university_name " + "FROM training_signup ts " + "LEFT JOIN users u ON ts.user_id = u.user_id " + "LEFT JOIN major m ON u.major_id = m.major_id " + "LEFT JOIN school s ON m.school_id = s.school_id " + "LEFT JOIN university uni ON s.university_id = uni.university_id " + "WHERE ts.train_id = #{trainId} " + "ORDER BY ts.create_time ASC")
    List<TrainingSignupVO> selectExportList(@Param("trainId") Long trainId);
}