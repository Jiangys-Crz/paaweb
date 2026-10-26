package com.cwnupaa.backend.support;

import com.cwnupaa.backend.entity.ExternalBoard;
import com.cwnupaa.backend.mapper.EventMapper;
import com.cwnupaa.backend.mapper.ExternalBoardMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

public final class ExternalBoardFixtures {
    private ExternalBoardFixtures() {}
    public static void attach(Object service, EventMapper events) {
        var boards = mock(ExternalBoardMapper.class);
        when(boards.selectById(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            var event = events.selectById(id);
            if (event == null) return null;
            var board = new ExternalBoard();
            BeanUtils.copyProperties(event, board);
            board.setBoardId(id);
            board.setEventId(id);
            return board;
        });
        ReflectionTestUtils.setField(service, "externalBoardMapper", boards);
    }
}
