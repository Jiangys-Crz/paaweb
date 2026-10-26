package com.cwnupaa.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.cwnupaa.backend.task.RatingTask;
import com.cwnupaa.backend.task.TrackerTask;
import com.cwnupaa.backend.task.EventSignupNotificationTask;
import com.cwnupaa.backend.service.qqbot.BotRuntime;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:paaweb_context;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@MockBean({RatingTask.class, TrackerTask.class, EventSignupNotificationTask.class, BotRuntime.class})
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
