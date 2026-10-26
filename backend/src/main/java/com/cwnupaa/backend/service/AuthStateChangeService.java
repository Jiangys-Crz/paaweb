package com.cwnupaa.backend.service;

import com.cwnupaa.backend.manager.SseNotificationManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Map;

/**
 * 在用户身份状态、角色或权限发生变化后通知在线客户端重新拉取 /user/me。
 * 数据库仍是唯一可信来源，事件中不携带权限内容，避免把旧快照继续传播给客户端。
 */
@Service
public class AuthStateChangeService {
    private final SseNotificationManager sseNotificationManager;

    public AuthStateChangeService(SseNotificationManager sseNotificationManager) {
        this.sseNotificationManager = sseNotificationManager;
    }

    public void notifyChanged(Long userId) {
        if (userId == null) return;

        Runnable push = () -> sseNotificationManager.sendEvent(
                userId,
                "auth-state-change",
                Map.of("userId", userId, "changedAt", Instant.now().toString()));

        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    push.run();
                }
            });
        } else {
            push.run();
        }
    }
}
