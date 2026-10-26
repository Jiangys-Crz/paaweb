package com.cwnupaa.backend.config;

import com.cwnupaa.backend.common.Result;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Real Boot datasource binding and advisor discovery, using an isolated in-memory DB. */
class SignupAdmissionBootTests {
    @Test void bootBindsPoolAndRunsAdmissionBeforeTransaction() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
                        DataSourceTransactionManagerAutoConfiguration.class, TransactionAutoConfiguration.class,
                        AopAutoConfiguration.class))
                .withUserConfiguration(SignupWriteAdmissionConfig.class, ProbeConfig.class)
                .withPropertyValues("spring.datasource.url=jdbc:h2:mem:signup_admission_boot;DB_CLOSE_DELAY=-1",
                        "spring.datasource.hikari.maximum-pool-size=30", "spring.datasource.hikari.minimum-idle=1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(HikariDataSource.class).getMaximumPoolSize()).isEqualTo(30);
                    var probe = context.getBean(Probe.class);
                    assertThat(((Advised) probe).getAdvisors()).hasSize(2);
                    assertThat(probe.write(Map.of("eventId", 1L)).getData()).isTrue();
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class ProbeConfig { @Bean Probe probe() { return new Probe(); } }

    public static class Probe {
        @SignupWriteGuard @Transactional
        public Result<Boolean> write(Map<String, Object> params) {
            return Result.success(TransactionSynchronizationManager.isActualTransactionActive());
        }
    }
}
