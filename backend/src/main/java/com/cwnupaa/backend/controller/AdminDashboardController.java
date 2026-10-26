package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.entity.CertificateVerify;
import com.cwnupaa.backend.entity.Event;
import com.cwnupaa.backend.entity.Notice;
import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.CertificateVerifyMapper;
import com.cwnupaa.backend.mapper.EventMapper;
import com.cwnupaa.backend.mapper.NoticeMapper;
import com.cwnupaa.backend.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/dashboard")
public class AdminDashboardController {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private EventMapper eventMapper;
    @Autowired
    private CertificateVerifyMapper certificateVerifyMapper;
    @Autowired
    private NoticeMapper noticeMapper;

    @GetMapping("/stats")
    public Result<Map<String, Object>> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        QueryWrapper<Users> userWrapper = new QueryWrapper<>();
        userWrapper.eq("account_status", "pending").isNotNull("student_card_file_id").gt("student_card_file_id", 0);
        stats.put("pendingUsers", userMapper.selectCount(userWrapper));

        QueryWrapper<Event> eventWrapper = new QueryWrapper<>();
        eventWrapper.eq("status", 1).gt("event_end", LocalDateTime.now());
        stats.put("ongoingEvents", eventMapper.selectCount(eventWrapper));

        QueryWrapper<CertificateVerify> certWrapper = new QueryWrapper<>();
        certWrapper.eq("status", "pending");
        stats.put("pendingCerts", certificateVerifyMapper.selectCount(certWrapper));

        LocalDateTime startOfMonth = LocalDateTime.of(LocalDate.now().withDayOfMonth(1), LocalTime.MIN);
        QueryWrapper<Notice> noticeWrapper = new QueryWrapper<>();
        noticeWrapper.ge("create_time", startOfMonth);
        stats.put("newNotices", noticeMapper.selectCount(noticeWrapper));

        return Result.success(stats);
    }
}