package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.dto.SubmissionQueryDTO;
import com.cwnupaa.backend.entity.OjPracticeRecord;
import com.cwnupaa.backend.entity.OjRecentContest;
import com.cwnupaa.backend.service.TrackerService;
import com.cwnupaa.backend.vo.TrackerDashboardVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tracker")
public class TrackerController {

    private final TrackerService trackerService;

    @Autowired
    public TrackerController(TrackerService trackerService) {
        this.trackerService = trackerService;
    }

    @PostMapping("/refresh")
    public Result<String> refreshMyStats(
            @RequestAttribute("userId") Long userId,
            @RequestParam(required = false) String luoguClientId,
            @RequestParam(required = false) List<String> platforms
    ) {
        try {
            trackerService.refreshUserTracker(userId, luoguClientId, platforms);
            return Result.success("同步请求已接收，后台正在执行中。请稍后留意站内通知，在此期间您可以自由浏览其他页面。");
        } catch (Exception e) {
            return Result.error("刷新请求提交失败: " + e.getMessage());
        }
    }

    @GetMapping("/dashboard")
    public Result<TrackerDashboardVO> getDashboard(
            @RequestAttribute(value = "userId", required = false) Long currentUserId,
            @RequestParam(required = false) Long userId
    ) {
        try {
            Long targetId = (userId != null) ? userId : currentUserId;
            if (targetId == null) return Result.error("未指定用户");

            TrackerDashboardVO vo = trackerService.getDashboardStats(targetId);
            return Result.success(vo);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(TrackerController.class).warn("操作失败，请检查请求参数和服务配置");
            return Result.error("获取统计数据失败: " + e.getMessage());
        }
    }

    @GetMapping("/upcoming-contests")
    public Result<List<OjRecentContest>> getUpcomingContests() {
        return Result.success(trackerService.getUpcomingContests());
    }

    @PostMapping("/submissions")
    public Result<IPage<OjPracticeRecord>> searchSubmissions(
            @RequestAttribute("userId") Long userId,
            @RequestBody SubmissionQueryDTO queryDTO
    ) {
        queryDTO.setUserId(userId);
        return Result.success(trackerService.searchSubmissions(queryDTO));
    }
}