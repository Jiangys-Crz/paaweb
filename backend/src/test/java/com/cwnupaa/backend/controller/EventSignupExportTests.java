package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.cwnupaa.backend.entity.Event;
import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.*;
import org.apache.ibatis.session.SqlSession;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventSignupExportTests {
    private SqlSession session;
    private EventController controller;

    @BeforeEach
    void setUp() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:signup_export_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        String common = "signup_id BIGINT PRIMARY KEY, event_id BIGINT, status VARCHAR, type VARCHAR, "
                + "signup_time TIMESTAMP, is_ranking INT, advisor VARCHAR, contest_account VARCHAR, "
                + "contest_password VARCHAR, seat_number VARCHAR, created_by_user_id BIGINT, signup_source VARCHAR";
        jdbc.execute("CREATE TABLE signup_personal (" + common + ", user_id BIGINT)");
        jdbc.execute("CREATE TABLE signup_team (" + common + ", source_team_id BIGINT, team_name VARCHAR)");
        jdbc.execute("CREATE TABLE users (user_id BIGINT PRIMARY KEY, real_name VARCHAR, number_id VARCHAR)");
        jdbc.execute("CREATE TABLE signup_team_detail (detail_id BIGINT, signup_team_id BIGINT, user_id BIGINT, "
                + "member_order INT, contest_account VARCHAR, contest_password VARCHAR, seat_number VARCHAR)");
        for (long id = 1; id <= 155; id++) {
            String status = id <= 139 ? "approved" : id <= 152 ? "cancelled" : "rejected";
            String type = id % 5 == 0 ? "girl" : "official";
            jdbc.update("INSERT INTO users VALUES (?, ?, ?)", id, "个人" + id, String.valueOf(id));
            // Identical timestamps exercise stable ordering across the page boundary.
            jdbc.update("INSERT INTO signup_personal (signup_id,event_id,user_id,status,type,signup_time) "
                    + "VALUES (?,1,?,?,?,TIMESTAMP '2026-11-01 09:00:00')", id, id, status, type);
            jdbc.update("INSERT INTO signup_team (signup_id,event_id,team_name,status,type,signup_time) "
                    + "VALUES (?,2,?,?,?,TIMESTAMP '2026-11-01 09:00:00')", id, "队伍" + id, status, type);
            for (int member = 0; member < 2; member++) {
                long userId = 1000 + id * 2 + member;
                jdbc.update("INSERT INTO users VALUES (?, ?, ?)", userId, "成员" + userId, String.valueOf(userId));
                jdbc.update("INSERT INTO signup_team_detail (detail_id,signup_team_id,user_id,member_order) VALUES (?,?,?,?)",
                        userId, id, userId, member);
            }
        }
        var config = new MybatisConfiguration();
        config.addMapper(SignupPersonalMapper.class);
        config.addMapper(SignupTeamMapper.class);
        config.addMapper(SignupTeamDetailMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(config);
        var plugin = new MybatisPlusInterceptor();
        plugin.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        factory.setPlugins(plugin);
        session = factory.getObject().openSession(true);
        controller = new EventController();
        ReflectionTestUtils.setField(controller, "signupPersonalMapper", session.getMapper(SignupPersonalMapper.class));
        ReflectionTestUtils.setField(controller, "signupTeamMapper", session.getMapper(SignupTeamMapper.class));
        ReflectionTestUtils.setField(controller, "signupTeamDetailMapper", session.getMapper(SignupTeamDetailMapper.class));
        var events = mock(EventMapper.class);
        when(events.selectById(anyLong())).thenAnswer(call -> {
            long id = call.getArgument(0);
            var event = new Event();
            event.setEventId(id); event.setName("测试比赛"); event.setIsTeam(id == 2 ? 1 : 0);
            return event;
        });
        ReflectionTestUtils.setField(controller, "eventMapper", events);
        var users = mock(UserMapper.class);
        when(users.selectById(anyLong())).thenAnswer(call -> user(call.getArgument(0)));
        when(users.selectBatchIds(any())).thenAnswer(call -> {
            Collection<Long> ids = call.getArgument(0);
            return ids.stream().map(this::user).toList();
        });
        ReflectionTestUtils.setField(controller, "userMapper", users);
        ReflectionTestUtils.setField(controller, "majorMapper", mock(MajorMapper.class));
    }

    @AfterEach
    void closeSession() {
        if (session != null) session.close();
    }

    @Test
    void exportsAll139ApprovedPeopleWhileTheInteractiveListRemainsCapped() throws Exception {
        var page = controller.listSignups(1L, 1, 100000, null, "approved", null, "summary").getData();
        assertThat(page.getTotal()).isEqualTo(139);
        assertThat(page.getRecords()).hasSize(100);
        assertThat(export(1L, "summary", null, "approved", null, "姓名"))
                .containsExactlyElementsOf(descendingNames("个人", 139));
        assertThat(export(1L, "summary", null, null, null, "姓名")).hasSize(155);
    }

    @Test
    void exportsEveryTeamAndEveryMemberInBothViews() throws Exception {
        assertThat(export(2L, "summary", null, "approved", null, "队伍名称"))
                .containsExactlyElementsOf(descendingNames("队伍", 139));
        List<String> members = new ArrayList<>();
        for (int id = 139; id >= 1; id--) {
            members.add("成员" + (1000 + id * 2));
            members.add("成员" + (1001 + id * 2));
        }
        assertThat(export(2L, "detail", null, "approved", null, "姓名")).containsExactlyElementsOf(members);
    }

    @Test
    void preservesKeywordStatusAndTypeFiltersAndHandlesEmptyResults() throws Exception {
        assertThat(export(1L, "summary", "个人", "approved", "official", "姓名")).hasSize(139);
        assertThat(export(1L, "summary", "个人", "approved", "girl", "姓名"))
                .containsExactlyElementsOf(IntStream.iterate(135, id -> id >= 5, id -> id - 5).mapToObj(id -> "个人" + id).toList());
        assertThat(export(2L, "summary", "队伍", "approved", "official", "队伍名称")).hasSize(139);
        assertThat(export(2L, "detail", "成员", "approved", "girl", "姓名")).hasSize(54).doesNotHaveDuplicates();
        assertThat(export(1L, "summary", "不存在", "approved", "girl", "姓名")).isEmpty();
        assertThat(export(1L, "summary", null, "cancelled", null, "姓名")).hasSize(13);
    }

    private List<String> export(long eventId, String view, String keyword, String status, String type, String column) throws Exception {
        var response = new MockHttpServletResponse();
        controller.exportSignups(eventId, view, keyword, status, type, response);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet = workbook.getSheetAt(0);
            int index = -1;
            for (var cell : sheet.getRow(0)) if (column.equals(cell.getStringCellValue())) index = cell.getColumnIndex();
            assertThat(index).isNotNegative();
            List<String> values = new ArrayList<>();
            for (int row = 1; row <= sheet.getLastRowNum(); row++) values.add(sheet.getRow(row).getCell(index).getStringCellValue());
            return values;
        }
    }

    private List<String> descendingNames(String prefix, int count) {
        return IntStream.iterate(count, id -> id >= 1, id -> id - 1).mapToObj(id -> prefix + id).toList();
    }

    private Users user(Long id) {
        var user = new Users();
        user.setUserId(id); user.setRealName((id >= 1000 ? "成员" : "个人") + id);
        user.setNumberId(String.valueOf(id)); user.setMajorId(1L); user.setGender(1); user.setGradeYear("2026");
        return user;
    }
}
