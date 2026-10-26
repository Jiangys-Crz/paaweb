package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.dto.UserPracticeStatsDTO;
import com.cwnupaa.backend.service.AdminDataPanelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/data-panel")
@Tag(name = "Admin Data Panel", description = "Admin Data Panel APIs")
public class AdminDataPanelController {

    @Autowired
    private AdminDataPanelService adminDataPanelService;

    @GetMapping("/stats")
    @Operation(summary = "Get overall statistics")
    public Result<Map<String, Object>> getOverallStats(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long majorId,
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Long universityId,
            @RequestParam(required = false) String className,
            @RequestParam(required = false) String gradeYear,
            @RequestParam(required = false) Integer isEnrolledStu,
            @RequestParam(required = false) String eduBg) {
        return Result.success(adminDataPanelService.getOverallStats(keyword, majorId, schoolId, universityId, className, gradeYear, isEnrolledStu, eduBg));
    }

    @GetMapping("/users")
    @Operation(summary = "Get user practice statistics")
    public Result<IPage<UserPracticeStatsDTO>> getUserStats(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long majorId,
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Long universityId,
            @RequestParam(required = false) String className,
            @RequestParam(required = false) String gradeYear,
            @RequestParam(required = false) Integer isEnrolledStu,
            @RequestParam(required = false) String eduBg) {
        return Result.success(adminDataPanelService.getUserStats(page, size, keyword, majorId, schoolId, universityId, className, gradeYear, isEnrolledStu, eduBg));
    }

    @GetMapping("/export")
    @Operation(summary = "Export user practice statistics")
    public void exportUserStats(
            HttpServletResponse response,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long majorId,
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Long universityId,
            @RequestParam(required = false) String className,
            @RequestParam(required = false) String gradeYear,
            @RequestParam(required = false) Integer isEnrolledStu,
            @RequestParam(required = false) String eduBg) {
        adminDataPanelService.exportUserStats(response, keyword, majorId, schoolId, universityId, className, gradeYear, isEnrolledStu, eduBg);
    }
}
