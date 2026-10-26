package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.service.ExternalBoardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/external")
public class ExternalSubmitController {

    @Autowired
    private ExternalBoardService externalBoardService;

    @Autowired
    private com.cwnupaa.backend.mapper.ExternalAppMapper externalAppMapper;

    // 保持原推送 URL、API Key 和 payload 格式，路径 ID 现在表示外榜 ID
    @PostMapping("/board/{boardId}/submissions")
    public Result<String> receiveSubmissionsBatch(@PathVariable("boardId") Long boardId, @RequestBody Map<String, Object> payload) {
        try {
            // 1. 提取认证信息 (API Key)
            String apiKey = (String) payload.get("api_key");

            if (apiKey == null || apiKey.trim().isEmpty()) {
                return Result.error("未提供 API Key");
            }

            // 2. 验证 API Key
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.cwnupaa.backend.entity.ExternalApp> appQuery = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            appQuery.eq("app_key", apiKey).eq("status", 1);
            com.cwnupaa.backend.entity.ExternalApp app = externalAppMapper.selectOne(appQuery);

            if (app == null) {
                return Result.error("认证失败：API Key 无效或已被禁用");
            }
            if (!Boolean.TRUE.equals(app.getExternalBoardEnabled())) {
                return Result.error(403, "API Key 未被授予外榜权限");
            }

            // 3. 提取批量提交记录
            Object submissionsObj = payload.get("submissions");
            if (!(submissionsObj instanceof List)) {
                return Result.error("数据格式错误，submissions必须为数组");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> submissions = (List<Map<String, Object>>) submissionsObj;
            if (submissions.isEmpty()) {
                return Result.success("接收成功，但提交记录为空");
            }

            // 4. 调用Service进行批量处理并优化性能
            int savedCount = externalBoardService.processExternalSubmissionsBatch(boardId, submissions);

            return Result.success("成功处理并保存了 " + savedCount + " 条提交记录");

        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(ExternalSubmitController.class).warn("操作失败，请检查请求参数和服务配置");
            return Result.error("批量接收失败: " + e.getMessage());
        }
    }

    @PostMapping("/board/{boardId}/problems")
    public Result<String> receiveProblemsBatch(@PathVariable("boardId") Long boardId, @RequestBody Map<String, Object> payload) {
        try {
            String apiKey = (String) payload.get("api_key");
            if (apiKey == null || apiKey.trim().isEmpty()) {
                return Result.error("未提供 API Key");
            }

            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.cwnupaa.backend.entity.ExternalApp> appQuery = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            appQuery.eq("app_key", apiKey).eq("status", 1);
            com.cwnupaa.backend.entity.ExternalApp app = externalAppMapper.selectOne(appQuery);

            if (app == null) {
                return Result.error("认证失败：API Key 无效或已被禁用");
            }
            if (!Boolean.TRUE.equals(app.getExternalBoardEnabled())) {
                return Result.error(403, "API Key 未被授予外榜权限");
            }

            Object problemsObj = payload.get("problems");
            if (!(problemsObj instanceof List)) {
                return Result.error("数据格式错误，problems必须为数组");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> problems = (List<Map<String, Object>>) problemsObj;
            if (problems.isEmpty()) {
                return Result.success("接收成功，但题目列表为空");
            }

            int savedCount = externalBoardService.processExternalProblemsBatch(boardId, problems);
            return Result.success("成功更新了 " + savedCount + " 个题目信息");

        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(ExternalSubmitController.class).warn("操作失败，请检查请求参数和服务配置");
            return Result.error("批量接收题目失败: " + e.getMessage());
        }
    }
}
