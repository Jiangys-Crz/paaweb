package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.entity.ExternalApp;
import com.cwnupaa.backend.mapper.ExternalAppMapper;
import com.cwnupaa.backend.dto.ExternalAppPermissionsDTO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/app-key")

public class AdminAppKeyController {

    @Autowired
    private ExternalAppMapper externalAppMapper;


    @GetMapping("/list")
    public Result<List<ExternalApp>> list() {
        return Result.success(externalAppMapper.selectList(new QueryWrapper<ExternalApp>().orderByDesc("app_id")));
    }

    @PostMapping("/generate")
    public Result<String> generate(@RequestParam String appName,
                                   @RequestParam(defaultValue = "false") boolean externalLoginEnabled,
                                   @RequestParam(defaultValue = "false") boolean externalBoardEnabled) {
        if (!StringUtils.hasText(appName) || appName.trim().length() > 50) {
            return Result.error("网站名称不能为空且不能超过50字");
        }
        String newKey = "ak_" + UUID.randomUUID().toString().replace("-", "") + System.currentTimeMillis();

        ExternalApp app = new ExternalApp();
        app.setAppName(appName.trim());
        app.setAppKey(newKey);
        app.setStatus(1);
        app.setExternalLoginEnabled(externalLoginEnabled);
        app.setExternalBoardEnabled(externalBoardEnabled);
        app.setCreateTime(LocalDateTime.now());

        externalAppMapper.insert(app);
        return Result.success("生成成功");
    }

    @PostMapping("/toggle-status")
    public Result<String> toggleStatus(@RequestParam Long appId, @RequestParam Integer status) {
        if (appId == null || appId <= 0 || status == null || (status != 0 && status != 1)) {
            return Result.error("密钥ID或状态无效");
        }
        if (externalAppMapper.update(null, new UpdateWrapper<ExternalApp>()
                .eq("app_id", appId).set("status", status)) == 0) {
            return Result.error("密钥不存在或已被删除");
        }
        return Result.success("状态已更新");
    }

    @PostMapping("/permissions")
    public Result<String> updatePermissions(@RequestBody @Valid ExternalAppPermissionsDTO dto) {
        if (externalAppMapper.update(null, new UpdateWrapper<ExternalApp>().eq("app_id", dto.appId())
                .set("external_login_enabled", dto.externalLoginEnabled())
                .set("external_board_enabled", dto.externalBoardEnabled())) == 0) {
            return Result.error("密钥不存在或已被删除");
        }
        return Result.success("权限已更新");
    }

    @DeleteMapping("/{appId}")
    public Result<String> delete(@PathVariable Long appId) {
        if (appId == null || appId <= 0) return Result.error("密钥ID无效");
        if (externalAppMapper.deleteById(appId) == 0) return Result.error("密钥不存在或已被删除");
        return Result.success("密钥已删除");
    }
}
