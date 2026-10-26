package com.cwnupaa.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cwnupaa.backend.entity.RatingComposition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RatingCompositionMapper extends BaseMapper<RatingComposition> {
    @Select("SELECT COUNT(*) + 1 FROM rating_composition WHERE rating_all > #{rating}")
    Integer getRank(Integer rating);

    @Select("SELECT COUNT(*) + 1 FROM rating_composition r " + "LEFT JOIN users u ON r.user_id = u.user_id " + "WHERE r.rating_all > #{rating} AND u.is_enrolled_stu = 1")
    Integer getRankForEnrolled(Integer rating);
}