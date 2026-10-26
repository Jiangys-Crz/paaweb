package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.entity.Notification;
import com.cwnupaa.backend.manager.SseNotificationManager;
import com.cwnupaa.backend.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/notification")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SseNotificationManager sseNotificationManager;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestAttribute("userId") Long userId) {
        return sseNotificationManager.createEmitter(userId);
    }

    @GetMapping("/list")
    public Result<IPage<Notification>> getList(@RequestAttribute("userId") Long userId, @RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(notificationService.getMyNotifications(userId, page, size));
    }

    @GetMapping("/unread-count")
    public Result<Long> getUnreadCount(@RequestAttribute("userId") Long userId) {
        return Result.success(notificationService.getUnreadCount(userId));
    }

    @PostMapping("/read/{msgId}")
    public Result<String> markAsRead(@RequestAttribute("userId") Long userId, @PathVariable Long msgId) {
        notificationService.markAsRead(userId, msgId);
        return Result.success("操作成功");
    }

    @PostMapping("/read-all")
    public Result<String> markAllAsRead(@RequestAttribute("userId") Long userId) {
        notificationService.markAllAsRead(userId);
        return Result.success("操作成功");
    }
}