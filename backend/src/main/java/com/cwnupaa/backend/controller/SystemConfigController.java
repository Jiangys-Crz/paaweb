package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.dto.ObjectStorageConfigDTO;
import com.cwnupaa.backend.dto.SmtpConfigDTO;
import com.cwnupaa.backend.entity.AdminPermissions;
import com.cwnupaa.backend.mapper.AdminPermissionsMapper;
import com.cwnupaa.backend.service.FileStorageService;
import com.cwnupaa.backend.service.SystemConfigService;
import com.cwnupaa.backend.utils.SecurityUtils;
import com.cwnupaa.backend.vo.UserProfileVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/system-config")
public class SystemConfigController {
    private final SystemConfigService systemConfigService;
    private final FileStorageService fileStorageService;
    private final AdminPermissionsMapper adminPermissionsMapper;
    private final ObjectMapper objectMapper;

    public SystemConfigController(SystemConfigService systemConfigService,
                                  FileStorageService fileStorageService,
                                  AdminPermissionsMapper adminPermissionsMapper,
                                  ObjectMapper objectMapper) {
        this.systemConfigService = systemConfigService;
        this.fileStorageService = fileStorageService;
        this.adminPermissionsMapper = adminPermissionsMapper;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/smtp")
    public Result<SmtpConfigDTO> getSmtp(HttpServletRequest request) {
        requireManageSystem(request);
        SmtpConfigDTO config = systemConfigService.getSmtpConfig();
        config.setPassword(null);
        return Result.success(config);
    }

    @PutMapping("/smtp")
    public Result<String> saveSmtp(@RequestBody SmtpConfigDTO config, HttpServletRequest request) {
        UserProfileVO user = requireManageSystem(request);
        systemConfigService.saveSmtpConfig(config, user.getUserId());
        return Result.success("SMTP 配置已保存并生效");
    }

    @PostMapping("/smtp/test")
    public Result<String> testSmtp(@RequestBody SmtpConfigDTO incoming, HttpServletRequest request) {
        requireManageSystem(request);
        SmtpConfigDTO config = systemConfigService.mergeSmtpSecret(incoming);
        config.setEnabled(true);
        systemConfigService.validateSmtp(config);
        try {
            JavaMailSenderImpl sender = (JavaMailSenderImpl) systemConfigService.buildMailSender(config);
            sender.testConnection();
            return Result.success("SMTP 连接测试成功");
        } catch (Exception e) {
            return Result.error("SMTP 连接失败：" + safeMessage(e));
        }
    }

    @GetMapping("/storage")
    public Result<ObjectStorageConfigDTO> getStorage(HttpServletRequest request) {
        requireManageSystem(request);
        ObjectStorageConfigDTO config = systemConfigService.getObjectStorageConfig();
        config.setSecretKey(null);
        return Result.success(config);
    }

    @PutMapping("/storage")
    public Result<String> saveStorage(@RequestBody ObjectStorageConfigDTO config, HttpServletRequest request) {
        UserProfileVO user = requireManageSystem(request);
        systemConfigService.saveObjectStorageConfig(config, user.getUserId());
        return Result.success("对象存储配置已保存并生效");
    }

    @PostMapping("/storage/test")
    public Result<String> testStorage(@RequestBody ObjectStorageConfigDTO incoming, HttpServletRequest request) {
        requireManageSystem(request);
        try {
            fileStorageService.test(incoming);
            return Result.success("对象存储连接及 Bucket 访问测试成功");
        } catch (Exception e) {
            return Result.error("对象存储连接失败：" + safeMessage(e));
        }
    }

    private UserProfileVO requireManageSystem(HttpServletRequest request) {
        UserProfileVO user = SecurityUtils.currentUser(request);
        if (user == null) throw new IllegalArgumentException("未登录或会话已失效");
        if (("supper_admin".equals(user.getRole()) || "super_admin".equals(user.getRole()))
                && "admin".equals(user.getUserAccount())) return user;
        AdminPermissions row = adminPermissionsMapper.selectOne(
                new QueryWrapper<AdminPermissions>().eq("user_id", user.getUserId()));
        if (row != null && StringUtils.hasText(row.getPermissions())) {
            try {
                List<String> permissions = objectMapper.readValue(row.getPermissions(), new TypeReference<List<String>>() {});
                if (permissions.contains("manage_sys")) return user;
            } catch (Exception ignored) {
            }
        }
        throw new IllegalArgumentException("无权限操作系统配置");
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return StringUtils.hasText(message) && message.length() <= 300 ? message : "请检查服务地址、凭据与网络连接";
    }
}
