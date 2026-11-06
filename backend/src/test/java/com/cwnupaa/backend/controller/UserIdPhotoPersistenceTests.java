package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.cwnupaa.backend.entity.FileSystem;
import com.cwnupaa.backend.mapper.UserMapper;
import com.cwnupaa.backend.mapper.AccountSecurityMapper;
import com.cwnupaa.backend.service.*;
import com.cwnupaa.backend.vo.UserProfileVO;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real mapper SQL and transactions, without starting the production application. */
@SpringJUnitConfig(UserIdPhotoPersistenceTests.Config.class)
@MockBean({EmailService.class, AuthStateChangeService.class, SecurityAuditService.class, CertificateGrantService.class})
class UserIdPhotoPersistenceTests {
    @Autowired private UserController controller;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @MockBean private FileStorageService storage;
    private MockMvc mvc;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        reset(storage);
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
                    id_photo_file_id BIGINT REFERENCES file_system(file_id), id_photo_updated_at TIMESTAMP,
                    register_time TIMESTAMP, student_card_file_id BIGINT, is_enrolled_stu INTEGER)
                """);
        jdbc.update("INSERT INTO users (user_id, user_account, user_name, role, account_status, major_id) "
                + "VALUES (8, 'student8', '示例乙', 'student', 'normal', 12)");
        jdbc.update("INSERT INTO file_system (file_id, type, uploader_id, storage_type, storage_key, access_purpose) "
                + "VALUES (90, 'png', 8, 'LOCAL', 'sensitive/id_photo/new.png', 'IDENTITY')");
        var profile = new UserProfileVO();
        profile.setUserId(8L);
        profile.setUserName("示例乙");
        profile.setRole("student");
        profile.setAccountStatus("normal");
        profile.setMajorName("原专业");
        session = new MockHttpSession();
        session.setAttribute("user", profile);
        session.setAttribute(AccountSecurityService.AUTH_VERSION_ATTRIBUTE, 0L);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void firstUploadPersistsAndCanBeReadWithoutPublicContentOrProfileTables() throws Exception {
        // The unrelated university/notice/rating/OJ tables are deliberately absent.
        mvc.perform(post("/user/id-photo").param("fileId", "90").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        assertThat(photoId()).isEqualTo(90L);
        assertThat(jdbc.queryForObject("SELECT id_photo_updated_at FROM users WHERE user_id=8", Object.class)).isNotNull();
        assertThat(snapshot().getIdPhotoFileId()).isEqualTo(90L);
        assertThat(snapshot().getIdPhotoUrl()).isEqualTo("/api/user/id-photo/8");
        assertThat(snapshot().getMajorName()).isEqualTo("原专业");
        assertThat(jdbc.queryForObject("SELECT user_name FROM users WHERE user_id=8", String.class)).isEqualTo("示例乙");
        assertThat(jdbc.queryForObject("SELECT account_status FROM users WHERE user_id=8", String.class)).isEqualTo("normal");
        assertThat(jdbc.queryForObject("SELECT auth_version FROM users WHERE user_id=8", Long.class)).isZero();
        byte[] image = new byte[]{1, 2, 3};
        when(storage.readBytes(any(FileSystem.class))).thenReturn(image);
        mvc.perform(get("/user/id-photo/8").session(session)).andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(content().bytes(image));
    }

    @Test
    void oldFileCleanupSqlFailureDoesNotRollBackTheNewPhotoOrSession() throws Exception {
        oldPhoto();
        mvc.perform(post("/user/id-photo").param("fileId", "90").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        assertThat(photoId()).isEqualTo(90L);
        assertThat(snapshot().getIdPhotoFileId()).isEqualTo(90L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM file_system WHERE file_id=91", Long.class)).isEqualTo(1L);
        verify(storage, never()).delete(any());
    }

    @Test
    void unreferencedOldFileIsDeletedAfterReplacementCommits() throws Exception {
        oldPhoto();
        referenceTables();
        mvc.perform(post("/user/id-photo").param("fileId", "90").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        assertThat(photoId()).isEqualTo(90L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM file_system WHERE file_id=91", Long.class)).isZero();
        verify(storage).delete(argThat(file -> file.getFileId().equals(91L)));
    }

    @Test
    void oldFileStillReferencedByAnotherUserIsRetained() throws Exception {
        oldPhoto();
        jdbc.update("INSERT INTO users (user_id, id_photo_file_id) VALUES (9, 91)");
        mvc.perform(post("/user/id-photo").param("fileId", "90").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        assertThat(photoId()).isEqualTo(90L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM file_system WHERE file_id=91", Long.class)).isEqualTo(1L);
        verify(storage, never()).delete(any());
    }

    @Test
    void transactionRollbackKeepsThePreviousPhotoSessionAndFile() throws Exception {
        oldPhoto();
        var request = new MockHttpServletRequest();
        request.setSession(session);
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            assertThat(controller.updateIdPhoto(90L, request).getCode()).isEqualTo(200);
            assertThat(snapshot().getIdPhotoFileId()).isEqualTo(91L);
            transaction.setRollbackOnly();
        });
        assertThat(photoId()).isEqualTo(91L);
        assertThat(snapshot().getIdPhotoFileId()).isEqualTo(91L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM file_system WHERE file_id=91", Long.class)).isEqualTo(1L);
        verify(storage, never()).delete(any());
    }

    @Test
    void databaseWriteFailureKeepsThePreviousPhotoSessionAndFile() throws Exception {
        oldPhoto();
        jdbc.execute("ALTER TABLE users ADD CONSTRAINT reject_new_photo CHECK (id_photo_file_id <> 90)");
        var request = new MockHttpServletRequest();
        request.setSession(session);
        assertThatThrownBy(() -> controller.updateIdPhoto(90L, request))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(photoId()).isEqualTo(91L);
        assertThat(snapshot().getIdPhotoFileId()).isEqualTo(91L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM file_system WHERE file_id=91", Long.class)).isEqualTo(1L);
        verify(storage, never()).delete(any());
    }

    @Test
    void reusingTheCurrentPhotoDoesNotDeleteIt() throws Exception {
        jdbc.update("UPDATE users SET id_photo_file_id=90 WHERE user_id=8");
        mvc.perform(post("/user/id-photo").param("fileId", "90").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        assertThat(photoId()).isEqualTo(90L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM file_system WHERE file_id=90", Long.class)).isEqualTo(1L);
        verify(storage, never()).delete(any());
    }

    @Test
    void anotherUsersPhotoIsRejected() throws Exception {
        jdbc.update("UPDATE file_system SET uploader_id=9 WHERE file_id=90");
        mvc.perform(post("/user/id-photo").param("fileId", "90").session(session))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("只能使用自己上传的文件"));
        assertThat(photoId()).isNull();
    }

    @Test
    void nonImageIdentityUploadIsRejected() throws Exception {
        jdbc.update("UPDATE file_system SET type='pdf' WHERE file_id=90");
        mvc.perform(post("/user/id-photo").param("fileId", "90").session(session))
                .andExpect(jsonPath("$.msg").value("只能引用图片文件"));
        assertThat(photoId()).isNull();
    }

    @Test
    void publicGeneralImageCannotBeBoundAsPrivateIdentityMaterial() throws Exception {
        referenceTables();
        jdbc.update("UPDATE file_system SET access_purpose='GENERAL', storage_key='ordinary.png' WHERE file_id=90");
        jdbc.update("UPDATE users SET avatar_file_id=90 WHERE user_id=8");
        mvc.perform(post("/user/id-photo").param("fileId", "90").session(session))
                .andExpect(jsonPath("$.msg").value("已公开展示的文件不能作为私有实名材料，请重新上传"));
        assertThat(photoId()).isNull();
        assertThat(jdbc.queryForObject("SELECT access_purpose FROM file_system WHERE file_id=90", String.class)).isEqualTo("GENERAL");
    }

    @Test
    void revokedSessionCannotBindAPhoto() throws Exception {
        jdbc.update("UPDATE users SET auth_version=1 WHERE user_id=8");
        mvc.perform(post("/user/id-photo").param("fileId", "90").session(session))
                .andExpect(jsonPath("$.code").value(401));
        assertThat(photoId()).isNull();
    }

    private Long photoId() {
        return jdbc.queryForObject("SELECT id_photo_file_id FROM users WHERE user_id=8", Long.class);
    }

    private UserProfileVO snapshot() { return (UserProfileVO) session.getAttribute("user"); }

    private void oldPhoto() {
        jdbc.update("INSERT INTO file_system (file_id, type, uploader_id, storage_type, storage_key, access_purpose) "
                + "VALUES (91, 'png', 8, 'LOCAL', 'sensitive/id_photo/old.png', 'IDENTITY')");
        jdbc.update("UPDATE users SET id_photo_file_id=91 WHERE user_id=8");
        snapshot().setIdPhotoFileId(91L);
        snapshot().setIdPhotoUrl("/api/user/id-photo/8");
    }

    private void referenceTables() {
        for (String ddl : List.of(
                "CREATE TABLE certificate_verify (file_id BIGINT)",
                "CREATE TABLE certificate_type (url_id BIGINT)",
                "CREATE TABLE certificate_issue (template_file_id BIGINT)",
                "CREATE TABLE school_admin_application (file_id BIGINT)",
                "CREATE TABLE audit_records (file_id BIGINT, biz_type VARCHAR(50))",
                "CREATE TABLE file_relation (file_id BIGINT, biz_type VARCHAR(50))",
                "CREATE TABLE university (logo_file_id BIGINT)",
                "CREATE TABLE school (logo_file_id BIGINT)",
                "CREATE TABLE contest_category (logo_file_id BIGINT, intro TEXT)",
                "CREATE TABLE notice (cover_file_id BIGINT, content TEXT)",
                "CREATE TABLE association_member (avatar_file_id BIGINT, introduction TEXT)",
                "CREATE TABLE team (team_image_id BIGINT, introduce TEXT)",
                "CREATE TABLE sys_intro (category VARCHAR(50), content TEXT)",
                "CREATE TABLE event (description TEXT)",
                "CREATE TABLE training (description TEXT)")) jdbc.execute(ddl);
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    @MapperScan("com.cwnupaa.backend.mapper")
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:id_photo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
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
