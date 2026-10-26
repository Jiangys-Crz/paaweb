package com.cwnupaa.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.UserMapper;
import com.cwnupaa.backend.vo.UserProfileVO;
import com.cwnupaa.backend.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class UserBanService {
    private final UserMapper users;
    private final AuthStateChangeService auth;
    private final AccountSecurityService accountSecurity;
    private final SecurityAuditService audit;
    public UserBanService(UserMapper users, AuthStateChangeService auth, AccountSecurityService accountSecurity, SecurityAuditService audit) {
        this.users = users;
        this.auth = auth;
        this.accountSecurity = accountSecurity;
        this.audit = audit;
    }
    static LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Shanghai")); }

    @Transactional
    public Users ban(Long id, LocalDateTime until, String reason, UserProfileVO operator) {
        if (id == null || until == null || !until.isAfter(now())) throw new IllegalArgumentException("请选择未来的解封时间");
        if (!StringUtils.hasText(reason) || reason.trim().length() > 1000) throw new IllegalArgumentException("封禁原因不能为空且不能超过1000字");
        Users user = users.selectOne(new QueryWrapper<Users>().eq("user_id", id).last("FOR UPDATE"));
        if (user == null) throw new IllegalArgumentException("用户不存在");
        if (!SecurityUtils.canManageTarget(operator, user) || !SecurityUtils.hasPermission(operator, "manage_user_status")) throw new IllegalArgumentException("无权限封禁该账号");
        if (id.equals(operator.getUserId()) || "admin".equals(user.getUserAccount())
                || "super_admin".equals(user.getRole()) || "supper_admin".equals(user.getRole()))
            throw new IllegalArgumentException("不能封禁自己或超级管理员账号");
        if (!StringUtils.hasText(user.getEmail())) throw new IllegalArgumentException("该用户未绑定邮箱，无法发送封禁通知");
        if ("banned".equals(user.getAccountStatus())) throw new IllegalArgumentException("该用户已被封禁");
        user.setPreBanStatus(user.getAccountStatus());
        user.setAccountStatus("banned");
        user.setBanUntil(until);
        user.setBanReason(reason.trim());
        users.updateById(user);
        accountSecurity.revokeSessions(user, null, false);
        audit.record(operator.getUserId(), id, "admin-user-ban", java.util.List.of("accountStatus", "banUntil", "banReason"));
        auth.notifyChanged(id);
        return user;
    }

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void releaseExpiredBans() {
        for (Users user : users.selectList(new QueryWrapper<Users>().eq("account_status", "banned").le("ban_until", now()))) {
            int changed = users.update(null, new UpdateWrapper<Users>().eq("user_id", user.getUserId())
                    .eq("account_status", "banned").eq("ban_until", user.getBanUntil())
                    .set("account_status", StringUtils.hasText(user.getPreBanStatus()) ? user.getPreBanStatus() : "restricted")
                    .set("ban_until", null).set("pre_ban_status", null));
            if (changed > 0) auth.notifyChanged(user.getUserId());
        }
    }
}
