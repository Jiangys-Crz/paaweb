package com.cwnupaa.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cwnupaa.backend.entity.ExternalBoard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ExternalBoardMapper extends BaseMapper<ExternalBoard> {
    @Select("SELECT * FROM external_board WHERE board_id = #{boardId} FOR UPDATE")
    ExternalBoard lockById(@Param("boardId") Long boardId);
}
