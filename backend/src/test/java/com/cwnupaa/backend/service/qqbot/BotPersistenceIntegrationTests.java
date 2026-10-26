package com.cwnupaa.backend.service.qqbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Opt-in PostgreSQL audit. Supply only an isolated disposable local database:
 * -Dqqbot.audit=true -Dqqbot.audit.jdbcUrl=jdbc:postgresql://127.0.0.1:PORT/DB
 * -Dqqbot.audit.username=USER -Dqqbot.audit.password=PASSWORD.
 * Each test creates and removes its own random schema. No application configuration,
 * Spring Boot application, bot connection, external service, or scheduler is loaded.
 */
@EnabledIfSystemProperty(named = "qqbot.audit", matches = "true")
class BotPersistenceIntegrationTests {
    private static final LocalDateTime NOW = LocalDateTime.of(2031, 10, 26, 12, 0);
    private static final String GROUP = "123456789";
    private static final String HYDRO_KEY = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90";
    private DriverManagerDataSource administrator;
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private ObjectMapper json;
    private AnnotationConfigApplicationContext context;
    private BotConfigService configs;
    private BotTaskService tasks;
    private BotDeliveryStore deliveries;
    private String schema;
    private Path schemaFile;

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Transactions {}

    @BeforeEach
    void setUpDisposableSchema() throws Exception {
        String url = required("qqbot.audit.jdbcUrl");
        // Refuse non-local targets even when the opt-in property was accidentally inherited.
        assertThat(url).as("Use the disposable PostgreSQL container on a loopback TCP port")
                .matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost|\\[::1\\]):[1-9][0-9]{0,4}/[A-Za-z_][A-Za-z0-9_]*(?:\\?.*)?");
        String username = required("qqbot.audit.username");
        String password = System.getProperty("qqbot.audit.password", "");
        administrator = new DriverManagerDataSource(url, username, password);
        schema = "qqbot_audit_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(administrator).execute("CREATE SCHEMA " + schema);
        dataSource = new DriverManagerDataSource(url, username, password) {
            @Override public Connection getConnection() throws SQLException {
                Connection connection = super.getConnection();
                connection.setSchema(schema);
                return connection;
            }
            @Override public Connection getConnection(String user, String pass) throws SQLException {
                Connection connection = super.getConnection(user, pass);
                connection.setSchema(schema);
                return connection;
            }
        };
        jdbc = new JdbcTemplate(dataSource);
        schemaFile = Path.of("../SQL/schema.sql");
        if (!Files.isRegularFile(schemaFile)) schemaFile = Path.of("SQL/schema.sql");
        assertThat(schemaFile).isRegularFile();
        initializeSchema();
        json = new ObjectMapper().registerModule(new JavaTimeModule());
        startServices();
    }

    @AfterEach
    void removeDisposableSchema() {
        if (context != null) context.close();
        if (administrator != null && schema != null) new JdbcTemplate(administrator).execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }

    private static String required(String property) {
        String value = System.getProperty(property);
        assertThat(value).as("Explicit isolated audit setting: " + property).isNotBlank();
        return value;
    }

    private void startServices() {
        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(JdbcTemplate.class, () -> jdbc);
        context.registerBean(ObjectMapper.class, () -> json);
        context.registerBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(dataSource));
        context.register(Transactions.class, BotConfigService.class, BotTaskService.class, BotDeliveryStore.class);
        context.refresh();
        configs = context.getBean(BotConfigService.class);
        tasks = context.getBean(BotTaskService.class);
        deliveries = context.getBean(BotDeliveryStore.class);
    }

    private void restartServices() {
        context.close();
        startServices();
    }

    private void initializeSchema() throws Exception {
        String sql = Files.readString(schemaFile).replace("public.", schema + ".")
                .replace("SET search_path = public;", "SET search_path = " + schema + ";");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private BotTask task(String recurrence, LocalDateTime dueAt) {
        BotTask task = new BotTask();
        task.setTitle("隔离测试定时消息");
        task.setGroupId(GROUP);
        task.setContent("消息文本及图片");
        task.setImages(new ArrayList<>(List.of("https://www.example.com/logo.png")));
        task.setDueAt(dueAt);
        task.setRecurrence(recurrence);
        if ("INTERVAL".equals(recurrence)) task.setIntervalMinutes(15);
        return task;
    }

    private void enqueue(String key, BotTask task) {
        deliveries.enqueue(key, task.getGroupId(), task.getId(), null,
                new BotDeliveryStore.Payload(task.getContent(), task.getImages(), task.getDueAt(), task.getRevision()));
        // The test clock is explicit; do not depend on the container session time zone.
        jdbc.update("UPDATE qq_bot_delivery SET available_at=? WHERE delivery_key=?", NOW.minusDays(1), BotDeliveryStore.hash(key + ":" + task.getGroupId()));
    }

    private String status(BotDeliveryStore.Delivery delivery) {
        return jdbc.queryForObject("SELECT status FROM qq_bot_delivery WHERE delivery_key=?", String.class, delivery.key());
    }

    private LocalDateTime availableAt(BotDeliveryStore.Delivery delivery) {
        return jdbc.queryForObject("SELECT available_at FROM qq_bot_delivery WHERE delivery_key=?", LocalDateTime.class, delivery.key());
    }

    @Test
    void configurationSurvivesRestartRedactsSecretsAndPreservesBlankUpdates() {
        BotConfig config = new BotConfig();
        config.setEnabled(true);
        config.setAccessToken("isolated-onebot-test-access-token");
        config.setHydroApiKey(HYDRO_KEY);
        config.setHydroBaseUrl("https://oj.example.com///");
        config.setPushGroups(new ArrayList<>(List.of(GROUP, GROUP, "987654321")));
        config.setPushTraining(true);
        configs.save(config, 10L);

        BotConfig publicConfig = configs.publicConfig();
        assertThat(publicConfig.getAccessToken()).isNull();
        assertThat(publicConfig.getHydroApiKey()).isNull();
        assertThat(publicConfig.isAccessTokenConfigured()).isTrue();
        assertThat(publicConfig.isHydroApiKeyConfigured()).isTrue();
        assertThat(publicConfig.getHydroBaseUrl()).isEqualTo("https://oj.example.com");
        assertThat(publicConfig.getPushGroups()).containsExactly(GROUP, "987654321");
        assertThat(configs.get().getAccessToken()).isEqualTo("isolated-onebot-test-access-token");
        publicConfig.getPushGroups().clear();
        assertThat(configs.get().getPushGroups()).containsExactly(GROUP, "987654321");

        BotConfig update = configs.publicConfig();
        update.setAccessToken("  ");
        update.setHydroApiKey(null);
        update.setTypingMillisPerChar(150);
        configs.save(update, 11L);
        restartServices();
        assertThat(configs.get().getAccessToken()).isEqualTo("isolated-onebot-test-access-token");
        assertThat(configs.get().getHydroApiKey()).isEqualTo(HYDRO_KEY);
        assertThat(configs.get().getTypingMillisPerChar()).isEqualTo(150);
        assertThat(jdbc.queryForObject("SELECT operator_id FROM qq_bot_config WHERE id=1", Long.class)).isEqualTo(11L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM qq_bot_config", Integer.class)).isEqualTo(1);

        BotConfig invalid = configs.publicConfig();
        invalid.setWsUrl("ws://user:password@127.0.0.1:3001");
        assertThatThrownBy(() -> configs.save(invalid, 12L)).isInstanceOf(IllegalArgumentException.class);
        restartServices();
        assertThat(configs.get().getWsUrl()).isEqualTo("ws://127.0.0.1:3001");
        assertThat(configs.publicConfig().getAccessToken()).isNull();
    }

    @Test
    void taskEditRemovesPendingOldRevisionAndLateCompletionCannotOverwriteNewRevision() {
        BotTask original = tasks.save(null, task("DAILY", NOW.minusMinutes(5)));
        enqueue("original-sending", original);
        BotDeliveryStore.Delivery oldDelivery = deliveries.claim(NOW);
        assertThat(oldDelivery).isNotNull();
        enqueue("original-pending", original);

        BotTask edit = task("INTERVAL", NOW.plusDays(1));
        edit.setContent("新版内容");
        BotTask changed = tasks.save(original.getId(), edit);
        assertThat(changed.getId()).isEqualTo(original.getId());
        assertThat(changed.getRevision()).isNotEqualTo(original.getRevision());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM qq_bot_delivery WHERE status='pending'", Integer.class)).isZero();
        assertThat(status(oldDelivery)).isEqualTo("sending");

        deliveries.complete(oldDelivery, NOW.plusSeconds(1));
        BotTask afterLateCompletion = tasks.find(changed.getId());
        assertThat(afterLateCompletion.getRevision()).isEqualTo(changed.getRevision());
        assertThat(afterLateCompletion.getDueAt()).isEqualTo(NOW.plusDays(1));
        assertThat(afterLateCompletion.getContent()).isEqualTo("新版内容");
        assertThat(afterLateCompletion.getLastSentAt()).isNull();
        assertThat(afterLateCompletion.isEnabled()).isTrue();

        enqueue("new-revision", changed);
        restartServices();
        assertThat(tasks.find(changed.getId()).getRevision()).isEqualTo(changed.getRevision());
        assertThat(tasks.due(NOW)).isEmpty();
        assertThat(tasks.due(NOW.plusDays(1))).extracting(BotTask::getId).containsExactly(changed.getId());
        tasks.delete(changed.getId());
        assertThat(tasks.find(changed.getId())).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM qq_bot_delivery WHERE status='pending'", Integer.class)).isZero();
        assertThatThrownBy(() -> tasks.save(original.getId(), task("ONCE", NOW)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不存在");
    }

    @Test
    void outboxUniqueKeyIncludesGroupAndKeepsSentDeduplicationAfterRestart() {
        var payload = new BotDeliveryStore.Payload("第一版", List.of(), NOW, null);
        deliveries.enqueue("event-key", GROUP, null, "online", payload);
        deliveries.enqueue("event-key", GROUP, null, "online", new BotDeliveryStore.Payload("重复事件不得覆盖", List.of(), NOW, null));
        deliveries.enqueue("event-key", "987654321", null, "online", payload);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM qq_bot_delivery", Integer.class)).isEqualTo(2);
        var first = deliveries.claim(NOW);
        assertThat(first).isNotNull();
        assertThat(first.payload().text()).isEqualTo("第一版");
        deliveries.complete(first, NOW);
        var second = deliveries.claim(NOW);
        assertThat(second).isNotNull();
        assertThat(second.groupId()).isNotEqualTo(first.groupId());
        deliveries.complete(second, NOW);
        restartServices();
        deliveries.enqueue("event-key", GROUP, null, "online", payload);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM qq_bot_delivery", Integer.class)).isEqualTo(2);
        assertThat(deliveries.claim(NOW.plusMinutes(1))).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM qq_bot_delivery WHERE status='sent'", Integer.class)).isEqualTo(2);
    }

    @Test
    void concurrentClaimsHaveOneWinnerAndAnExpiredLeaseIsRecoverable() throws Exception {
        deliveries.enqueue("parallel", GROUP, null, "board", new BotDeliveryStore.Payload("外榜", List.of(), NOW, null));
        int contenders = 8;
        CountDownLatch ready = new CountDownLatch(contenders), start = new CountDownLatch(1);
        var futures = new ArrayList<java.util.concurrent.Future<BotDeliveryStore.Delivery>>();
        try (var workers = Executors.newFixedThreadPool(contenders)) {
            for (int i = 0; i < contenders; i++) futures.add(workers.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return deliveries.claim(NOW);
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var winners = new ArrayList<BotDeliveryStore.Delivery>();
            for (var future : futures) {
                var delivery = future.get(15, TimeUnit.SECONDS);
                if (delivery != null) winners.add(delivery);
            }
            assertThat(winners).hasSize(1);
            assertThat(status(winners.getFirst())).isEqualTo("sending");
            assertThat(jdbc.queryForObject("SELECT attempts FROM qq_bot_delivery", Integer.class)).isEqualTo(1);
        } finally { start.countDown(); }

        restartServices();
        assertThat(deliveries.claim(NOW.plusMinutes(2))).isNull();
        BotDeliveryStore.Delivery recovered = deliveries.claim(NOW.plusMinutes(3).plusSeconds(1));
        assertThat(recovered).isNotNull();
        assertThat(recovered.attempts()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT attempts FROM qq_bot_delivery", Integer.class)).isEqualTo(2);
    }

    @Test
    void staleLeaseCannotCompleteFailOrCancelANewerClaimOrAdvanceItsTask() {
        BotTask scheduled = tasks.save(null, task("DAILY", NOW));
        enqueue("lease-token", scheduled);
        BotDeliveryStore.Delivery oldLease = deliveries.claim(NOW);
        assertThat(oldLease).isNotNull();
        assertThat(oldLease.attempts()).isEqualTo(1);
        LocalDateTime reclaimedAt = NOW.plusMinutes(3).plusSeconds(1);
        BotDeliveryStore.Delivery currentLease = deliveries.claim(reclaimedAt);
        assertThat(currentLease).isNotNull();
        assertThat(currentLease.key()).isEqualTo(oldLease.key());
        assertThat(currentLease.attempts()).isEqualTo(2);

        deliveries.complete(oldLease, reclaimedAt.plusSeconds(1));
        assertThat(status(currentLease)).isEqualTo("sending");
        assertThat(tasks.find(scheduled.getId()).getLastSentAt()).isNull();
        assertThat(tasks.find(scheduled.getId()).getDueAt()).isEqualTo(NOW);

        deliveries.fail(oldLease, reclaimedAt.plusSeconds(2));
        assertThat(status(currentLease)).isEqualTo("sending");
        assertThat(tasks.find(scheduled.getId()).getLastError()).isNull();
        deliveries.cancel(oldLease);
        assertThat(status(currentLease)).isEqualTo("sending");
        assertThat(jdbc.queryForObject("SELECT lease_until FROM qq_bot_delivery WHERE delivery_key=?", LocalDateTime.class,
                currentLease.key())).isEqualTo(reclaimedAt.plusMinutes(3));
        assertThat(jdbc.queryForObject("SELECT attempts FROM qq_bot_delivery WHERE delivery_key=?", Integer.class,
                currentLease.key())).isEqualTo(2);

        deliveries.complete(currentLease, reclaimedAt.plusSeconds(3));
        assertThat(status(currentLease)).isEqualTo("sent");
        assertThat(tasks.find(scheduled.getId()).getLastSentAt()).isEqualTo(reclaimedAt.plusSeconds(3));
        assertThat(tasks.find(scheduled.getId()).getDueAt()).isEqualTo(NOW.plusDays(1));
    }

    @Test
    void failuresBackOffWithoutAdvancingOccurrenceAndSuccessfulRetryClearsError() {
        BotTask scheduled = tasks.save(null, task("INTERVAL", NOW));
        enqueue("retry", scheduled);
        LocalDateTime clock = NOW;
        int[] delays = {30, 60, 120, 240, 480, 960, 1920, 3600, 3600};
        for (int attempt = 0; attempt < delays.length; attempt++) {
            var delivery = deliveries.claim(clock);
            assertThat(delivery).as("claim attempt " + attempt).isNotNull();
            assertThat(delivery.attempts()).isEqualTo(attempt + 1);
            deliveries.fail(delivery, clock);
            assertThat(status(delivery)).isEqualTo("pending");
            LocalDateTime nextAttempt = clock.plusSeconds(delays[attempt]);
            assertThat(availableAt(delivery)).isEqualTo(nextAttempt);
            assertThat(deliveries.claim(nextAttempt.minusNanos(1000000))).isNull();
            BotTask pending = tasks.find(scheduled.getId());
            assertThat(pending.getDueAt()).isEqualTo(NOW);
            assertThat(pending.getLastSentAt()).isNull();
            assertThat(pending.getLastError()).contains("发送失败");
            assertThat(pending.isEnabled()).isTrue();
            clock = nextAttempt;
        }
        var successful = deliveries.claim(clock);
        assertThat(successful).isNotNull();
        deliveries.complete(successful, clock);
        BotTask finished = tasks.find(scheduled.getId());
        assertThat(finished.getLastError()).isNull();
        assertThat(finished.getLastSentAt()).isEqualTo(clock);
        assertThat(finished.getDueAt()).isAfter(clock);
        assertThat(status(successful)).isEqualTo("sent");
        assertThat(jdbc.queryForObject("SELECT last_error FROM qq_bot_delivery WHERE delivery_key=?", String.class, successful.key())).isNull();
    }

    @Test
    void successfulCompletionDisablesOnceAndAdvancesAllRecurrencesPastMissedOccurrences() {
        for (String recurrence : List.of("ONCE", "DAILY", "WEEKLY", "INTERVAL")) {
            BotTask scheduled = tasks.save(null, task(recurrence, NOW));
            enqueue("complete-" + recurrence, scheduled);
            BotDeliveryStore.Delivery delivery = deliveries.claim(NOW);
            assertThat(delivery).isNotNull();
            LocalDateTime completedAt = switch (recurrence) {
                case "DAILY" -> NOW.plusDays(3).plusMinutes(17);
                case "WEEKLY" -> NOW.plusWeeks(3).plusMinutes(17);
                case "INTERVAL" -> NOW.plusMinutes(47);
                default -> NOW.plusMinutes(2);
            };
            deliveries.complete(delivery, completedAt);
            BotTask result = tasks.find(scheduled.getId());
            assertThat(result.getLastSentAt()).isEqualTo(completedAt);
            assertThat(result.getLastError()).isNull();
            assertThat(status(delivery)).isEqualTo("sent");
            if ("ONCE".equals(recurrence)) {
                assertThat(result.isEnabled()).isFalse();
                assertThat(result.getDueAt()).isEqualTo(NOW);
            } else {
                assertThat(result.isEnabled()).isTrue();
                assertThat(result.getDueAt()).isEqualTo(switch (recurrence) {
                    case "DAILY" -> NOW.plusDays(4);
                    case "WEEKLY" -> NOW.plusWeeks(4);
                    default -> NOW.plusHours(1);
                });
            }
            // A repeated completion of the old occurrence cannot advance a recurring task twice.
            deliveries.complete(delivery, completedAt.plusMinutes(1));
            BotTask repeated = tasks.find(scheduled.getId());
            assertThat(repeated.getDueAt()).isEqualTo(result.getDueAt());
            assertThat(repeated.getLastSentAt()).isEqualTo(result.getLastSentAt());
        }
    }

    @Test
    void serviceRestartPreservesExistingConfigTasksAndOutbox() throws Exception {
        BotConfig config = new BotConfig();
        config.setAccessToken("isolated-token");
        config.setHydroApiKey(HYDRO_KEY);
        configs.save(config, 20L);
        BotTask scheduled = tasks.save(null, task("DAILY", NOW));
        enqueue("preserved", scheduled);
        String configBefore = jdbc.queryForObject("SELECT payload FROM qq_bot_config WHERE id=1", String.class);
        String taskBefore = jdbc.queryForObject("SELECT payload FROM qq_bot_task WHERE id=?", String.class, scheduled.getId());
        String outboxBefore = jdbc.queryForObject("SELECT payload FROM qq_bot_delivery", String.class);
        restartServices();
        assertThat(jdbc.queryForObject("SELECT payload FROM qq_bot_config WHERE id=1", String.class)).isEqualTo(configBefore);
        assertThat(jdbc.queryForObject("SELECT payload FROM qq_bot_task WHERE id=?", String.class, scheduled.getId())).isEqualTo(taskBefore);
        assertThat(jdbc.queryForObject("SELECT payload FROM qq_bot_delivery", String.class)).isEqualTo(outboxBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=? AND table_name='oj_recent_contest' AND column_name='contests_endtime'", Integer.class, schema)).isEqualTo(1);
        restartServices();
        assertThat(configs.get().getHydroApiKey()).isEqualTo(HYDRO_KEY);
        assertThat(tasks.find(scheduled.getId()).getRevision()).isEqualTo(scheduled.getRevision());
        assertThat(deliveries.claim(NOW)).isNotNull();
    }
}
