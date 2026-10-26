package com.cwnupaa.backend.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@EnableScheduling
public class EventReminderTask {
    @Autowired
    private EventMapper eventMapper;
    @Autowired
    private SignupPersonalMapper signupPersonalMapper;
    @Autowired
    private SignupTeamMapper signupTeamMapper;
    @Autowired
    private SignupTeamDetailMapper signupTeamDetailMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private EmailService emailService;

    @Scheduled(cron = "0 0 12 * * ?")
    public void sendEventReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDateTime startOfDay = LocalDateTime.of(tomorrow, LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(tomorrow, LocalTime.MAX);

        List<Event> events = eventMapper.selectList(new QueryWrapper<Event>().ge("event_start", startOfDay).lt("event_start", endOfDay).eq("status", 1));

        if (events.isEmpty()) return;

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        for (Event event : events) {
            Map<String, String> targets = getEventParticipants(event);

            if (!targets.isEmpty()) {
                String eventTimeStr = event.getEventStart().format(dtf) + " ~ " + event.getEventEnd().format(dtf);
                String tip = "请提前到达比赛场地/准备好比赛环境。";

                emailService.sendEventReminderEmail(targets, event.getName(), eventTimeStr, tip);
                
            }
        }
    }

    private Map<String, String> getEventParticipants(Event event) {
        Map<String, String> targets = new HashMap<>();
        if (event.getIsTeam() == 0) {
            List<SignupPersonal> list = signupPersonalMapper.selectList(new QueryWrapper<SignupPersonal>().eq("event_id", event.getEventId()).eq("status", "approved"));
            for (SignupPersonal sp : list) {
                Users u = userMapper.selectById(sp.getUserId());
                if (u != null && StringUtils.hasText(u.getEmail())) {
                    targets.put(u.getEmail(), u.getRealName());
                }
            }
        } else {
            List<SignupTeam> list = signupTeamMapper.selectList(new QueryWrapper<SignupTeam>().eq("event_id", event.getEventId()).eq("status", "approved"));
            for (SignupTeam st : list) {
                List<SignupTeamDetail> details = signupTeamDetailMapper.selectList(new QueryWrapper<SignupTeamDetail>().eq("signup_team_id", st.getSignupId()));
                for (SignupTeamDetail detail : details) {
                    Users u = userMapper.selectById(detail.getUserId());
                    if (u != null && StringUtils.hasText(u.getEmail())) {
                        targets.put(u.getEmail(), u.getRealName());
                    }
                }
            }
        }
        return targets;
    }
}
