package com.cwnupaa.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cwnupaa.backend.entity.Event;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EventMapper extends BaseMapper<Event> {
    // All registration writers acquire this lock first, inside their transaction.
    // PostgreSQL holds it through commit, including across application instances.
    @Select("SELECT * FROM event WHERE event_id = #{eventId} FOR UPDATE")
    Event selectForSignupUpdate(@Param("eventId") Long eventId);
}
