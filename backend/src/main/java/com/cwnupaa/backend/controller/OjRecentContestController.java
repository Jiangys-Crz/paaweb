package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.entity.AdminPermissions;
import com.cwnupaa.backend.mapper.AdminPermissionsMapper;
import com.cwnupaa.backend.service.OjRecentContestService;
import com.cwnupaa.backend.utils.SecurityUtils;
import com.cwnupaa.backend.vo.OjRecentContestVO;
import com.cwnupaa.backend.vo.UserProfileVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/tracker/upcoming")
public class OjRecentContestController {

    private final OjRecentContestService ojRecentContestService;

    // 同步冷却：防止任意登录用户频繁触发爬虫
    private static final ConcurrentHashMap<String, Long> SYNC_COOLDOWN = new ConcurrentHashMap<>();
    private static final long SYNC_COOLDOWN_MILLIS = 5 * 60 * 1000;

    @Autowired
    private AdminPermissionsMapper adminPermissionsMapper;

    @Autowired
    public OjRecentContestController(OjRecentContestService ojRecentContestService) {
        this.ojRecentContestService = ojRecentContestService;
    }

    @GetMapping("/list")
    public Result<List<OjRecentContestVO>> getList() {
        return Result.success(ojRecentContestService.getUpcomingContestsVO());
    }

    @PostMapping("/sync")
    public Result<String> sync(HttpServletRequest request) {
        // 仅根管理员或持有 manage_tracker/manage_contest 权限者可触发同步
        UserProfileVO user = SecurityUtils.currentUser(request);
        if (user == null) return Result.error("未登录或会话已失效");
        String role = user.getRole();
        boolean topAdmin = "supper_admin".equals(role) || "super_admin".equals(role);
        if (!topAdmin && !hasTrackerPermission(user.getUserId())) {
            return Result.error("权限不足，无法触发赛事同步");
        }
        // 冷却（按用户维度，防止刷接口）
        Long last = SYNC_COOLDOWN.get(user.getUserId().toString());
        if (last != null && System.currentTimeMillis() - last < SYNC_COOLDOWN_MILLIS) {
            return Result.error("同步过于频繁，请稍后再试");
        }
        SYNC_COOLDOWN.put(user.getUserId().toString(), System.currentTimeMillis());
        try {
            ojRecentContestService.syncContests();
            return Result.success("同步指令已发送");
        } catch (Exception e) {
            return Result.error("同步失败: " + e.getMessage());
        }
    }

    private boolean hasTrackerPermission(Long userId) {
        if (userId == null) return false;
        AdminPermissions perm = adminPermissionsMapper.selectOne(new QueryWrapper<AdminPermissions>().eq("user_id", userId));
        if (perm == null || !StringUtils.hasText(perm.getPermissions())) return false;
        try {
            List<String> perms = new ObjectMapper().readValue(perm.getPermissions(), new TypeReference<List<String>>() {});
            return perms != null && (perms.contains("manage_tracker") || perms.contains("manage_contest"));
        } catch (Exception e) {
            return false;
        }
    }
}
