package com.cwnupaa.backend.mapper;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.cwnupaa.backend.controller.SchoolSignupAdminController;
import com.cwnupaa.backend.entity.SignupPersonal;
import com.cwnupaa.backend.entity.SignupTeam;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SignupTypeFilterTests {
    private SqlSession session;
    private SignupPersonalMapper personal;
    private SignupTeamMapper teams;

    @BeforeEach
    void setup() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:signup_types_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        String common = "signup_id BIGINT PRIMARY KEY, event_id BIGINT, status VARCHAR, type VARCHAR, "
                + "signup_time TIMESTAMP, is_ranking INT, advisor VARCHAR, contest_account VARCHAR, "
                + "contest_password VARCHAR, seat_number VARCHAR, created_by_user_id BIGINT, signup_source VARCHAR";
        jdbc.execute("CREATE TABLE signup_personal (" + common + ", user_id BIGINT)");
        jdbc.execute("CREATE TABLE signup_team (" + common + ", source_team_id BIGINT, team_name VARCHAR)");
        jdbc.execute("CREATE TABLE users (user_id BIGINT PRIMARY KEY, real_name VARCHAR, number_id VARCHAR)");
        jdbc.execute("CREATE TABLE signup_team_detail (detail_id BIGINT, signup_team_id BIGINT, user_id BIGINT)");
        String[] types = {"official", "girl", "star", null, "", "星芒初绽", "Python程序设计", "girl", "girl"};
        for (int index = 0; index < types.length; index++) {
            long id = index + 1;
            long eventId = id == 9 ? 2 : 1;
            String status = id == 8 ? "rejected" : "approved";
            jdbc.update("INSERT INTO users VALUES (?, ?, ?)", id, "成员" + id, "编号" + id);
            jdbc.update("INSERT INTO signup_personal (signup_id, event_id, user_id, status, type, signup_time) "
                    + "VALUES (?, ?, ?, ?, ?, DATEADD('SECOND', ?, TIMESTAMP '2026-10-01 09:00:00'))",
                    id, eventId, id, status, types[index], id);
            jdbc.update("INSERT INTO signup_team (signup_id, event_id, team_name, status, type, signup_time) "
                    + "VALUES (?, ?, ?, ?, ?, DATEADD('SECOND', ?, TIMESTAMP '2026-10-01 09:00:00'))",
                    id, eventId, "队伍" + id, status, types[index], id);
            jdbc.update("INSERT INTO signup_team_detail VALUES (?, ?, ?)", id, id, id);
        }
        // Multiple matching members must not duplicate the girls' team or inflate the total.
        jdbc.update("INSERT INTO signup_team_detail VALUES (100, 2, 2)");
        var config = new MybatisConfiguration();
        config.addMapper(SignupPersonalMapper.class);
        config.addMapper(SignupTeamMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(config);
        var plugin = new MybatisPlusInterceptor();
        plugin.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        factory.setPlugins(plugin);
        session = factory.getObject().openSession(true);
        personal = session.getMapper(SignupPersonalMapper.class);
        teams = session.getMapper(SignupTeamMapper.class);
    }

    @AfterEach
    void closeSession() {
        if (session != null) session.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void adminAndPublicFiltersIncludeGirlsWithAccuratePagination(boolean publicList) {
        assertBoth(publicList, "official", "approved", null, List.of(5L, 4L, 2L, 1L));
        assertBoth(publicList, "girl", "approved", null, List.of(2L));
        assertBoth(publicList, "star", "approved", null, List.of(3L));
        assertBoth(publicList, "星芒初绽", "approved", null, List.of(6L));
        assertBoth(publicList, "Python程序设计", "approved", null, List.of(7L));
        assertBoth(publicList, "official", "approved", "成员2", List.of(2L));
        assertBoth(publicList, "all", "approved", null, List.of(7L, 6L, 5L, 4L, 3L, 2L, 1L));
        if (!publicList) {
            assertBoth(false, "official", "all", null, List.of(8L, 5L, 4L, 2L, 1L));
            assertBoth(false, "official", "rejected", null, List.of(8L));
        }
    }

    private void assertBoth(boolean publicList, String type, String status, String keyword, List<Long> ids) {
        for (int index = 0; index < ids.size(); index++) {
            long pageNumber = index + 1;
            Page<SignupPersonal> personalPage = publicList
                    ? personal.selectPublicSignupPage(new Page<>(pageNumber, 1), 1L, status, type, keyword)
                    : personal.selectSignupPage(new Page<>(pageNumber, 1), 1L, status, type, keyword);
            Page<SignupTeam> teamPage = publicList
                    ? teams.selectPublicSignupPage(new Page<>(pageNumber, 1), 1L, status, type, keyword)
                    : teams.selectSignupPage(new Page<>(pageNumber, 1), 1L, status, type, keyword);
            assertThat(personalPage.getTotal()).isEqualTo(ids.size());
            assertThat(teamPage.getTotal()).isEqualTo(ids.size());
            assertThat(personalPage.getRecords()).extracting(SignupPersonal::getSignupId).containsExactly(ids.get(index));
            assertThat(teamPage.getRecords()).extracting(SignupTeam::getSignupId).containsExactly(ids.get(index));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"official", "girl", "star", "星芒初绽", "Python程序设计"})
    void schoolAdminUsesTheSameTypeMembership(String type) {
        var personalQuery = new QueryWrapper<SignupPersonal>().eq("event_id", 1L).eq("status", "approved");
        var teamQuery = new QueryWrapper<SignupTeam>().eq("event_id", 1L).eq("status", "approved");
        var controller = new SchoolSignupAdminController();
        ReflectionTestUtils.invokeMethod(controller, "applySignupTypeFilter", personalQuery, type);
        ReflectionTestUtils.invokeMethod(controller, "applySignupTypeFilter", teamQuery, type);
        List<Long> expected = switch (type) {
            case "official" -> List.of(1L, 2L, 4L, 5L);
            case "girl" -> List.of(2L);
            case "star" -> List.of(3L);
            case "星芒初绽" -> List.of(6L);
            default -> List.of(7L);
        };
        assertThat(personal.selectList(personalQuery)).extracting(SignupPersonal::getSignupId)
                .containsExactlyInAnyOrderElementsOf(expected);
        assertThat(teams.selectList(teamQuery)).extracting(SignupTeam::getSignupId)
                .containsExactlyInAnyOrderElementsOf(expected);
    }
}
