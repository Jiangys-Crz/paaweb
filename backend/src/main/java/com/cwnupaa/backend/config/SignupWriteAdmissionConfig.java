package com.cwnupaa.backend.config;

import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.dto.EventDTO;
import com.zaxxer.hikari.HikariDataSource;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.core.Ordered;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** Keep lock waiters outside the shared connection pool, through transaction commit. */
@Configuration(proxyBeanMethods = false)
public class SignupWriteAdmissionConfig {
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public Advisor signupWriteAdmissionAdvisor(DataSource dataSource) {
        int poolSize = dataSource instanceof HikariDataSource hikari ? hikari.getMaximumPoolSize() : 10;
        // Reserve at least half the connections for reads, login, boards and other services.
        var permits = new Semaphore(Math.max(1, poolSize / 2), true);
        var events = new HashMap<Long, EventQueue>();
        var pointcut = new StaticMethodMatcherPointcut() {
            @Override public boolean matches(Method method, Class<?> type) {
                return method.isAnnotationPresent(SignupWriteGuard.class);
            }
        };
        var advisor = new DefaultPointcutAdvisor(pointcut, (MethodInterceptor) invocation -> {
            Object argument = invocation.getArguments()[0];
            Object value = argument instanceof Map<?, ?> map ? map.get("eventId")
                    : argument instanceof EventDTO dto ? dto.getEventId() : null;
            if (value == null) return invocation.proceed(); // Let endpoint validation report missing parameters.
            Long eventId = Long.valueOf(value.toString());
            EventQueue queue;
            synchronized (events) {
                queue = events.computeIfAbsent(eventId, ignored -> new EventQueue());
                queue.references++;
            }
            boolean locked = false, admitted = false;
            try {
                locked = queue.lock.tryLock(3, TimeUnit.SECONDS);
                if (!locked) return Result.error(429, "该比赛正在处理其他报名操作，请稍后重试");
                admitted = permits.tryAcquire(3, TimeUnit.SECONDS);
                if (!admitted) return Result.error(429, "报名操作繁忙，请稍后重试");
                // The transaction advisor runs inside this call, so commit releases before us.
                // PostgreSQL's row lock still enforces correctness across application instances.
                return invocation.proceed();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return Result.error(429, "报名操作已中断，请重试");
            } finally {
                if (admitted) permits.release();
                if (locked) queue.lock.unlock();
                synchronized (events) {
                    if (--queue.references == 0) events.remove(eventId);
                }
            }
        });
        advisor.setOrder(Ordered.HIGHEST_PRECEDENCE + 100); // Before Spring's transaction advisor.
        return advisor;
    }

    private static final class EventQueue {
        final ReentrantLock lock = new ReentrantLock(true);
        int references;
    }
}
