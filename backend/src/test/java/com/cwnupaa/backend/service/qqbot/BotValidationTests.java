package com.cwnupaa.backend.service.qqbot;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class BotValidationTests {
    @Test void invalidConnectionsCredentialsAndGroupsAreRejected(){
        BotConfig c=new BotConfig();c.setEnabled(true);
        assertThatThrownBy(()->BotConfigService.validate(c)).isInstanceOf(IllegalArgumentException.class);
        c.setAccessToken("real-token");c.setWsUrl("ws://admin:password@localhost:3001?token=x");
        assertThatThrownBy(()->BotConfigService.validate(c)).isInstanceOf(IllegalArgumentException.class);
        c.setWsUrl("ws://localhost:3001");c.setAccessToken("token\r\nInjected: value");
        assertThatThrownBy(()->BotConfigService.validate(c)).isInstanceOf(IllegalArgumentException.class);
        c.setAccessToken("token");c.setPushGroups(List.of("12345","[CQ:at,qq=all]"));
        assertThatThrownBy(()->BotConfigService.validate(c)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void onlyPublicWebsiteImageLinksAreAccepted(){
        BotValidation.publicImage("https://www.example.com/api/file/image/42?public=1");
        for(String url:List.of("file:///etc/passwd","http://127.0.0.1/a.png","https://example.com.evil.test/a","https://example.com@evil.test/a","https://example.com:8080/a","base64://abc"))
            assertThatThrownBy(()->BotValidation.publicImage(url)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void recurrenceSkipsMissedRepeatsAndKeepsShanghaiWallClock(){
        BotTask t=new BotTask();t.setDueAt(LocalDateTime.of(2026,10,26,12,30));
        t.setRecurrence("DAILY");assertThat(BotTaskService.next(t,t.getDueAt().plusDays(5).plusHours(2))).isEqualTo(t.getDueAt().plusDays(6));
        t.setRecurrence("WEEKLY");assertThat(BotTaskService.next(t,t.getDueAt())).isEqualTo(t.getDueAt().plusWeeks(1));
        t.setRecurrence("INTERVAL");t.setIntervalMinutes(30);assertThat(BotTaskService.next(t,t.getDueAt().plusMinutes(65))).isEqualTo(t.getDueAt().plusMinutes(90));
        t.setRecurrence("ONCE");assertThat(BotTaskService.next(t,t.getDueAt())).isNull();
    }
    @Test void taskInputCannotSupplyProtocolActionsOrLocalImages(){
        BotTask t=new BotTask();t.setTitle("test");t.setGroupId("12345");t.setContent("[CQ:at,qq=all]");t.setDueAt(LocalDateTime.of(2026,10,26,12,30));
        BotTaskService.validate(t); // Plain text stays plain text; it is never parsed as CQ.
        t.setImages(List.of("file:///etc/passwd"));assertThatThrownBy(()->BotTaskService.validate(t)).isInstanceOf(IllegalArgumentException.class);
        t.setImages(List.of());t.setRecurrence("eval");assertThatThrownBy(()->BotTaskService.validate(t)).isInstanceOf(IllegalArgumentException.class);
    }
}
