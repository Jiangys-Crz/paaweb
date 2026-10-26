package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.cwnupaa.backend.mapper.AccountSecurityMapper;
import com.cwnupaa.backend.mapper.UserMapper;
import com.cwnupaa.backend.service.*;
import com.cwnupaa.backend.vo.UserProfileVO;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercise real SQL and commit/rollback, with unrelated profile/content tables absent. */
@SpringJUnitConfig(UserAuthPersistenceTests.Config.class)
@MockBean({EmailService.class, AuthStateChangeService.class, SecurityAuditService.class,
        CertificateGrantService.class, FileStorageService.class})
class UserAuthPersistenceTests {
    @Autowired private UserController controller;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    private MockMvc mvc;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("""
                CREATE TABLE file_system (
                    file_id BIGINT PRIMARY KEY, url VARCHAR(255), type VARCHAR(20), name VARCHAR(255),
                    uploader_id BIGINT, create_time TIMESTAMP, storage_type VARCHAR(20),
                    storage_key VARCHAR(255), access_purpose VARCHAR(32))
                """);
        jdbc.execute("""
                CREATE TABLE users (
                    user_id BIGINT PRIMARY KEY, user_account VARCHAR(20), user_name VARCHAR(50),
                    password VARCHAR(100), auth_version BIGINT DEFAULT 0, email VARCHAR(100),
                    phone VARCHAR(30), role VARCHAR(20), account_status VARCHAR(20),
                    ban_until TIMESTAMP, ban_reason VARCHAR(255), pre_ban_status VARCHAR(20),
                    introduce TEXT, real_name VARCHAR(50), number_id VARCHAR(50), gender INTEGER,
                    edu_bg VARCHAR(20), major_id BIGINT, grade_year VARCHAR(20), class_name VARCHAR(50),
                    avatar_file_id BIGINT, background_file_id BIGINT,
                    id_photo_file_id BIGINT, id_photo_updated_at TIMESTAMP, register_time TIMESTAMP,
                    student_card_file_id BIGINT REFERENCES file_system(file_id), is_enrolled_stu INTEGER)
                """);
        jdbc.update("INSERT INTO users (user_id, user_account, user_name, real_name, number_id, role, account_status, major_id) "
                + "VALUES (8, 'student8', '示例乙', '实名姓名', '20260008', 'student', 'register', 12)");
        jdbc.update("INSERT INTO file_system (file_id, url, type, uploader_id, storage_type, storage_key, access_purpose) "
                + "VALUES (90, '/file/image/90', 'png', 8, 'LOCAL', 'sensitive/student_card/new.png', 'IDENTITY')");
        var profile = new UserProfileVO();
        profile.setUserId(8L);
        profile.setRole("student");
        profile.setAccountStatus("register");
        profile.setMajorName("原专业");
        session = new MockHttpSession();
        session.setAttribute("user", profile);
        session.setAttribute(AccountSecurityService.AUTH_VERSION_ATTRIBUTE, 0L);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"register", "restricted"})
    void submissionCommitsMaterialAndPendingStatusWithoutProfileTables(String initialStatus) throws Exception {
        jdbc.update("UPDATE users SET account_status=? WHERE user_id=8", initialStatus);
        mvc.perform(post("/user/submit-auth").param("fileId", "90").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        assertThat(authFile()).isEqualTo(90L);
        assertThat(accountStatus()).isEqualTo("pending");
        // This is the predicate used by the admin's pending authentication list.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE account_status='pending' "
                + "AND student_card_file_id IS NOT NULL AND student_card_file_id>0", Integer.class)).isEqualTo(1);
        assertThat(snapshot().getAccountStatus()).isEqualTo("pending");
        assertThat(snapshot().getStudentCardFileId()).isEqualTo(90L);
        assertThat(snapshot().getMajorName()).isEqualTo("原专业");
        assertThat(jdbc.queryForObject("SELECT real_name FROM users WHERE user_id=8", String.class)).isEqualTo("实名姓名");
        assertThat(jdbc.queryForObject("SELECT auth_version FROM users WHERE user_id=8", Long.class)).isZero();
    }

    @Test
    void rolledBackSubmissionDoesNotPublishPendingSession() {
        var request = new MockHttpServletRequest();
        request.setSession(session);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            assertThat(controller.submitAuth(90L, request).getCode()).isEqualTo(200);
            assertThat(snapshot().getAccountStatus()).isEqualTo("register");
            tx.setRollbackOnly();
        });
        assertThat(authFile()).isNull();
        assertThat(accountStatus()).isEqualTo("register");
        assertThat(snapshot().getAccountStatus()).isEqualTo("register");
        assertThat(snapshot().getStudentCardFileId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"normal", "pending", "banned", "pending_activation"})
    void disallowedStatusCannotSubmit(String status) throws Exception {
        jdbc.update("UPDATE users SET account_status=? WHERE user_id=8", status);
        mvc.perform(post("/user/submit-auth").param("fileId", "90").session(session))
                .andExpect(jsonPath("$.code").value(500));
        assertThat(authFile()).isNull();
        assertThat(accountStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "99"})
    void invalidMaterialCannotEnterPendingList(String fileId) throws Exception {
        mvc.perform(post("/user/submit-auth").param("fileId", fileId).session(session))
                .andExpect(jsonPath("$.code").value(500));
        assertThat(authFile()).isNull();
        assertThat(accountStatus()).isEqualTo("register");
    }

    @Test
    void anotherUsersMaterialIsRejected() throws Exception {
        jdbc.update("UPDATE file_system SET uploader_id=9 WHERE file_id=90");
        mvc.perform(post("/user/submit-auth").param("fileId", "90").session(session))
                .andExpect(jsonPath("$.msg").value("只能使用自己上传的文件"));
        assertThat(authFile()).isNull();
        assertThat(accountStatus()).isEqualTo("register");
    }

    @Test
    void revokedSessionCannotSubmit() throws Exception {
        jdbc.update("UPDATE users SET auth_version=1 WHERE user_id=8");
        mvc.perform(post("/user/submit-auth").param("fileId", "90").session(session))
                .andExpect(jsonPath("$.code").value(401));
        assertThat(authFile()).isNull();
    }

    @Test
    void pendingApplicationCanBeRevokedWithoutProfileTables() throws Exception {
        pending();
        mvc.perform(post("/user/revoke-auth").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        assertThat(accountStatus()).isEqualTo("restricted");
        assertThat(authFile()).isEqualTo(90L);
        assertThat(snapshot().getAccountStatus()).isEqualTo("restricted");
        assertThat(snapshot().getMajorName()).isEqualTo("原专业");
    }

    @Test
    void rolledBackRevocationDoesNotPublishRestrictedSession() {
        pending();
        var request = new MockHttpServletRequest();
        request.setSession(session);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            assertThat(controller.revokeAuth(request).getCode()).isEqualTo(200);
            assertThat(snapshot().getAccountStatus()).isEqualTo("pending");
            tx.setRollbackOnly();
        });
        assertThat(accountStatus()).isEqualTo("pending");
        assertThat(snapshot().getAccountStatus()).isEqualTo("pending");
    }

    private void pending() {
        jdbc.update("UPDATE users SET account_status='pending', student_card_file_id=90 WHERE user_id=8");
        snapshot().setAccountStatus("pending");
        snapshot().setStudentCardFileId(90L);
    }

    private UserProfileVO snapshot() { return (UserProfileVO) session.getAttribute("user"); }
    private Long authFile() { return jdbc.queryForObject("SELECT student_card_file_id FROM users WHERE user_id=8", Long.class); }
    private String accountStatus() { return jdbc.queryForObject("SELECT account_status FROM users WHERE user_id=8", String.class); }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    @MapperScan("com.cwnupaa.backend.mapper")
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:user_auth;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            return factory.getObject();
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) { return new DataSourceTransactionManager(dataSource); }
        @Bean UserController userController() { return new UserController(); }
        @Bean FileAccessPolicy fileAccessPolicy() { return new FileAccessPolicy(); }
        @Bean AccountSecurityService accountSecurityService(UserMapper users, AccountSecurityMapper security) {
            return new AccountSecurityService(users, security);
        }
    }
}
