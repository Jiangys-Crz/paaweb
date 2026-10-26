package com.cwnupaa.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cwnupaa.backend.entity.Notification;
import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.NotificationMapper;
import com.cwnupaa.backend.mapper.UserMapper;
import com.cwnupaa.backend.manager.SseNotificationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class NotificationService {

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SseNotificationManager sseNotificationManager;

    @Async
    public void send(Long receiverId, Integer type, String title, String content) {
        if (receiverId == null) return;

        Notification notification = new Notification();
        notification.setReceiverId(receiverId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setIsRead(0);
        notification.setCreateTime(LocalDateTime.now());

        notificationMapper.insert(notification);
        
        // 实时推送通知
        sseNotificationManager.send(receiverId, notification);
    }

    public void sendByEmail(String email, Integer type, String title, String content) {
        Users user = userMapper.selectOne(new QueryWrapper<Users>().eq("email", email));
        if (user != null) {
            send(user.getUserId(), type, title, content);
        }
    }

    public IPage<Notification> getMyNotifications(Long userId, Integer page, Integer size) {
        Page<Notification> pageParam = new Page<>(page, size);
        QueryWrapper<Notification> query = new QueryWrapper<>();
        query.eq("receiver_id", userId).orderByDesc("create_time");
        return notificationMapper.selectPage(pageParam, query);
    }

    public Long getUnreadCount(Long userId) {
        return notificationMapper.selectCount(new QueryWrapper<Notification>().eq("receiver_id", userId).eq("is_read", 0));
    }

    public void markAsRead(Long userId, Long msgId) {
        Notification notification = notificationMapper.selectById(msgId);
        if (notification != null && notification.getReceiverId().equals(userId)) {
            notification.setIsRead(1);
            notificationMapper.updateById(notification);
        }
    }

    public void markAllAsRead(Long userId) {
        Notification updateEntity = new Notification();
        updateEntity.setIsRead(1);

        notificationMapper.update(updateEntity, new QueryWrapper<Notification>().eq("receiver_id", userId).eq("is_read", 0));
    }
}