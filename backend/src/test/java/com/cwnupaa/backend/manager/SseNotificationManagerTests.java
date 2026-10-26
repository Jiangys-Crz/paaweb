package com.cwnupaa.backend.manager;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class SseNotificationManagerTests {

    @Test
    void replacesPreviousConnectionForSameUser() {
        SseNotificationManager manager = new SseNotificationManager();
        try {
            SseEmitter first = manager.createEmitter(42L);
            SseEmitter second = manager.createEmitter(42L);

            assertThat(second).isNotSameAs(first);
            assertThat(manager.activeConnectionCount()).isEqualTo(1);
        } finally {
            manager.destroy();
        }
    }
}
