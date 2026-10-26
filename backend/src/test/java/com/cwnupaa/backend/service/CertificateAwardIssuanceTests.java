package com.cwnupaa.backend.service;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.cwnupaa.backend.mapper.CertificateTypeMapper;
import com.cwnupaa.backend.mapper.UserMapper;
import com.cwnupaa.backend.vo.CertificateImportResultVO;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.cwnupaa.backend.service.CertificateExcelImportServiceTests.workbook;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Real transactional issuance, including a competing request waiting on the template lock. */
@SpringJUnitConfig(CertificateAwardIssuanceTests.Config.class)
@MockBean({EmailService.class, UserMapper.class})
class CertificateAwardIssuanceTests {
    @Autowired private CertificateExcelImportService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserMapper users;
    @Autowired private PlatformTransactionManager transactions;

    @BeforeEach
    void setUp() {
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("""
                CREATE TABLE certificate_type (
                    certificate_type_id BIGINT PRIMARY KEY, type_name VARCHAR(200), type_id BIGINT,
                    url_id BIGINT, time DATE, term BIGINT, level_scope VARCHAR(20), update_user_id BIGINT,
                    font_size INTEGER, is_team INTEGER, template_schema TEXT, rating_enabled BOOLEAN)
                """);
        jdbc.execute("""
                CREATE TABLE certificate_issue (
                    issue_id BIGSERIAL PRIMARY KEY, certificate_type_id BIGINT NOT NULL,
                    template_file_id BIGINT, certificate_no VARCHAR(100) NOT NULL UNIQUE,
                    certificate_kind INTEGER NOT NULL, level_scope VARCHAR(20), team_name VARCHAR(200),
                    award_name VARCHAR(200) NOT NULL, issue_date DATE NOT NULL, status VARCHAR(20) NOT NULL,
                    template_schema_snapshot TEXT, created_by BIGINT, created_at TIMESTAMP, revoked_at TIMESTAMP)
                """);
        jdbc.execute("""
                CREATE TABLE certificate_issue_member (
                    issue_member_id BIGSERIAL PRIMARY KEY, issue_id BIGINT NOT NULL,
                    member_name VARCHAR(100), student_id VARCHAR(50), school_name VARCHAR(100),
                    user_id BIGINT, sort_order INTEGER,
                    UNIQUE (issue_id, school_name, student_id))
                """);
        jdbc.execute("""
                CREATE TABLE certificate_grant (
                    certificate_download_id BIGSERIAL PRIMARY KEY, certificate_type_id BIGINT,
                    user_id BIGINT, create_time TIMESTAMP, custom_name VARCHAR(500), user_account VARCHAR(50),
                    real_name VARCHAR(100), school_name VARCHAR(100), group_id VARCHAR(100), issue_id BIGINT)
                """);
        for (long typeId = 1; typeId <= 3; typeId++) {
            jdbc.update("""
                    INSERT INTO certificate_type
                        (certificate_type_id, type_name, time, level_scope, is_team, template_schema)
                    VALUES (?, '算法比赛证书', DATE '2026-10-25', '校级', ?, '{"version":2}')
                    """, typeId, typeId == 2 ? 1 : 0);
        }
        when(users.selectList(any())).thenReturn(List.of());
    }

    @Test
    void issuesDifferentPersonalAwardsAndRejectsTheSameAwardAcrossImports() throws Exception {
        service.issue(1L, personal("一等奖", "P-1"), 9L);
        service.issue(1L, personal("最佳创意奖", "P-2"), 9L);

        var duplicate = personal("一等奖", "P-3");
        var preview = service.preview(1L, duplicate);
        assertThat(preview.isValid()).isFalse();
        assertThat(preview.getErrors()).anyMatch(error -> "奖项".equals(error.getColumnName())
                && error.getMessage().contains("P-1"));
        assertThatThrownBy(() -> service.issue(1L, duplicate, 9L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("同一奖项只能有一条记录");
        assertThat(count("certificate_issue")).isEqualTo(2);
        assertThat(count("certificate_issue_member")).isEqualTo(2);
        assertThat(count("certificate_grant")).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT award_name FROM certificate_issue ORDER BY issue_id", String.class))
                .containsExactly("一等奖", "最佳创意奖");
    }

    @Test
    void issuesDifferentTeamAwardsAndRejectsReorderedOrRenamedDuplicates() throws Exception {
        service.issue(2L, team("算法队", "一等奖", "T-1", false), 9L);
        service.issue(2L, team("算法队", "最佳创意奖", "T-2", true), 9L);

        var duplicate = team("新队名", "一等奖", "T-3", true);
        assertThat(service.preview(2L, duplicate).isValid()).isFalse();
        assertThatThrownBy(() -> service.issue(2L, duplicate, 9L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("该队伍");
        assertThat(count("certificate_issue")).isEqualTo(2);
        assertThat(count("certificate_issue_member")).isEqualTo(4);
        assertThat(count("certificate_grant")).isEqualTo(4);
    }

    @Test
    void scopesTheAwardRuleToEachTemplateAndPreservesGlobalNumberUniqueness() throws Exception {
        service.issue(1L, personal("一等奖", "P-1"), 9L);
        service.issue(3L, personal("一等奖", "P-2"), 9L);
        var reusedNumber = personal("最佳创意奖", "P-1");
        assertThatThrownBy(() -> service.issue(3L, reusedNumber, 9L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("编号已存在");
        assertThat(count("certificate_issue")).isEqualTo(2);
    }

    @Test
    void keepsRevokedAwardsAsRecordsThatCannotBeDuplicated() throws Exception {
        service.issue(1L, personal("一等奖", "P-1"), 9L);
        jdbc.update("UPDATE certificate_issue SET status='revoked', revoked_at=CURRENT_TIMESTAMP");
        assertThatThrownBy(() -> service.issue(1L, personal("一等奖", "P-2"), 9L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("同一奖项只能有一条记录");
        service.issue(1L, personal("最佳创意奖", "P-3"), 9L);
        assertThat(count("certificate_issue")).isEqualTo(2);
    }

    @Test
    void rejectsAnEntireWorkbookWhenOnlyOneRowDuplicatesAnExistingAward() throws Exception {
        service.issue(1L, personal("一等奖", "P-1"), 9L);
        var mixed = workbook(false,
                new String[]{"李四", "002", "示例大学丙", "一等奖", "P-2"},
                new String[]{"张三", "001", "示例大学", "一等奖", "P-3"});
        assertThatThrownBy(() -> service.issue(1L, mixed, 9L)).isInstanceOf(IllegalArgumentException.class);
        assertThat(count("certificate_issue")).isEqualTo(1);
        assertThat(count("certificate_issue_member")).isEqualTo(1);
        assertThat(count("certificate_grant")).isEqualTo(1);
    }

    @Test
    void concurrentRequestsWaitForCommitAndCannotIssueTheSameAwardTwice() throws Exception {
        var firstFile = personal("一等奖", "P-1");
        var secondFile = personal("一等奖", "P-2");
        var firstIssued = new CountDownLatch(1);
        var allowCommit = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                CertificateImportResultVO result = service.issue(1L, firstFile, 9L);
                firstIssued.countDown();
                try {
                    if (!allowCommit.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Commit was not released");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return result;
            }));
            assertThat(firstIssued.await(10, TimeUnit.SECONDS)).isTrue();
            var second = workers.submit(() -> {
                secondStarted.countDown();
                return service.issue(1L, secondFile, 9L);
            });
            assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            allowCommit.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).getCertificateCount()).isEqualTo(1);
            assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(IllegalArgumentException.class)
                    .hasRootCauseMessage("导入未执行：该个人在当前证书模板下已存在“一等奖”记录（编号：P-1），同一奖项只能有一条记录");
            assertThat(count("certificate_issue")).isEqualTo(1);
            assertThat(count("certificate_issue_member")).isEqualTo(1);
            assertThat(count("certificate_grant")).isEqualTo(1);
        } finally {
            allowCommit.countDown();
            workers.shutdownNow();
        }
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private MockMultipartFile personal(String award, String number) throws Exception {
        return workbook(false, new String[]{"张三", "001", "示例大学", award, number});
    }

    private MockMultipartFile team(String name, String award, String number, boolean reversed) throws Exception {
        return workbook(true, reversed
                ? new String[]{name, "李四", "002", "示例大学丙", "张三", "001", "示例大学", award, number}
                : new String[]{name, "张三", "001", "示例大学", "李四", "002", "示例大学丙", award, number});
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    @MapperScan(basePackageClasses = CertificateTypeMapper.class)
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:certificate_awards;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) { return new DataSourceTransactionManager(dataSource); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            return factory.getObject();
        }
        @Bean CertificateExcelImportService importService() { return new CertificateExcelImportService(); }
    }
}
