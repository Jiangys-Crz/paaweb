package com.cwnupaa.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.dto.ObjectStorageConfigDTO;
import com.cwnupaa.backend.dto.SmtpConfigDTO;
import com.cwnupaa.backend.entity.SysIntro;
import com.cwnupaa.backend.mapper.SysIntroMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Properties;

@Service
public class SystemConfigService {
    public static final String SMTP_CATEGORY = "smtp_config";
    public static final String STORAGE_CATEGORY = "object_storage_config";

    private final SysIntroMapper sysIntroMapper;
    private final ObjectMapper objectMapper;

    @Value("${spring.mail.host:}") private String defaultMailHost;
    @Value("${spring.mail.port:587}") private int defaultMailPort;
    @Value("${spring.mail.username:}") private String defaultMailUsername;
    @Value("${spring.mail.password:}") private String defaultMailPassword;
    @Value("${spring.mail.protocol:smtp}") private String defaultMailProtocol;

    @Value("${spring.mail.properties.mail.smtp.ssl.enable:false}") private boolean defaultMailSslEnabled;
    @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}") private boolean defaultMailStartTlsEnabled;

    public SystemConfigService(SysIntroMapper sysIntroMapper, ObjectMapper objectMapper) {
        this.sysIntroMapper = sysIntroMapper;
        this.objectMapper = objectMapper;
    }

    public SmtpConfigDTO getSmtpConfig() {
        SmtpConfigDTO config = read(SMTP_CATEGORY, SmtpConfigDTO.class);
        if (config == null) {
            config = new SmtpConfigDTO();
            config.setHost(defaultMailHost);
            config.setPort(defaultMailPort);
            config.setUsername(defaultMailUsername);
            config.setPassword(defaultMailPassword);
            config.setProtocol(defaultMailProtocol);
            config.setSslEnabled(defaultMailSslEnabled);
            config.setStartTlsEnabled(defaultMailStartTlsEnabled);
        }
        config.setPasswordConfigured(StringUtils.hasText(config.getPassword()));
        return config;
    }

    public ObjectStorageConfigDTO getObjectStorageConfig() {
        ObjectStorageConfigDTO config = read(STORAGE_CATEGORY, ObjectStorageConfigDTO.class);
        if (config == null) config = new ObjectStorageConfigDTO();
        config.setSecretKeyConfigured(StringUtils.hasText(config.getSecretKey()));
        return config;
    }

    public SmtpConfigDTO mergeSmtpSecret(SmtpConfigDTO incoming) {
        if (!StringUtils.hasText(incoming.getPassword())) incoming.setPassword(getSmtpConfig().getPassword());
        incoming.setPasswordConfigured(StringUtils.hasText(incoming.getPassword()));
        return incoming;
    }

    public ObjectStorageConfigDTO mergeStorageSecret(ObjectStorageConfigDTO incoming) {
        if (!StringUtils.hasText(incoming.getSecretKey())) incoming.setSecretKey(getObjectStorageConfig().getSecretKey());
        incoming.setSecretKeyConfigured(StringUtils.hasText(incoming.getSecretKey()));
        return incoming;
    }

    public void saveSmtpConfig(SmtpConfigDTO incoming, Long operatorId) {
        validateSmtp(mergeSmtpSecret(incoming));
        save(SMTP_CATEGORY, incoming, operatorId);
    }

    public void saveObjectStorageConfig(ObjectStorageConfigDTO incoming, Long operatorId) {
        validateStorage(mergeStorageSecret(incoming));
        save(STORAGE_CATEGORY, incoming, operatorId);
    }

    public JavaMailSender buildMailSender(SmtpConfigDTO config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.getHost());
        sender.setPort(config.getPort());
        sender.setUsername(config.getUsername());
        sender.setPassword(config.getPassword());
        sender.setProtocol(StringUtils.hasText(config.getProtocol()) ? config.getProtocol() : "smtp");
        sender.setDefaultEncoding("UTF-8");
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", String.valueOf(config.isAuth()));
        properties.put("mail.smtp.ssl.enable", String.valueOf(config.isSslEnabled()));
        properties.put("mail.smtp.ssl.required", String.valueOf(config.isSslEnabled()));
        properties.put("mail.smtp.starttls.enable", String.valueOf(config.isStartTlsEnabled()));
        // 与 application.yml 保持一致：批量发送逐封建连，必须设超时，避免单封卡死拖住整批。
        properties.put("mail.smtp.connectiontimeout", "15000");
        properties.put("mail.smtp.timeout", "60000");
        properties.put("mail.smtp.writetimeout", "60000");
        return sender;
    }

    public void validateSmtp(SmtpConfigDTO config) {
        if (!config.isEnabled()) return;
        if (!StringUtils.hasText(config.getHost())) throw new IllegalArgumentException("SMTP 服务器不能为空");
        if (config.getPort() < 1 || config.getPort() > 65535) throw new IllegalArgumentException("SMTP 端口不合法");
        if (!StringUtils.hasText(config.getUsername())) throw new IllegalArgumentException("SMTP 用户名不能为空");
        if (!StringUtils.hasText(config.getPassword())) throw new IllegalArgumentException("SMTP 密码或授权码不能为空");
    }

    public void validateStorage(ObjectStorageConfigDTO config) {
        if (!config.isEnabled()) return;
        validateStorageForRead(config);
    }

    public void validateStorageForRead(ObjectStorageConfigDTO config) {
        if (!StringUtils.hasText(config.getEndpoint())) throw new IllegalArgumentException("对象存储服务地址不能为空");
        if (!StringUtils.hasText(config.getRegion())) throw new IllegalArgumentException("对象存储区域不能为空");
        if (!StringUtils.hasText(config.getBucket())) throw new IllegalArgumentException("对象存储 Bucket 不能为空");
        if (!StringUtils.hasText(config.getAccessKey())) throw new IllegalArgumentException("对象存储 Access Key 不能为空");
        if (!StringUtils.hasText(config.getSecretKey())) throw new IllegalArgumentException("对象存储 Secret Key 不能为空");
        try {
            java.net.URI.create(config.getEndpoint());
        } catch (Exception e) {
            throw new IllegalArgumentException("对象存储服务地址格式不正确");
        }
    }

    private <T> T read(String category, Class<T> type) {
        SysIntro row = sysIntroMapper.selectOne(new QueryWrapper<SysIntro>().eq("category", category));
        if (row == null || !StringUtils.hasText(row.getContent())) return null;
        try {
            return objectMapper.readValue(row.getContent(), type);
        } catch (Exception e) {
            throw new IllegalStateException("系统配置解析失败：" + category, e);
        }
    }

    private void save(String category, Object config, Long operatorId) {
        try {
            SysIntro row = sysIntroMapper.selectOne(new QueryWrapper<SysIntro>().eq("category", category));
            if (row == null) {
                row = new SysIntro();
                row.setCategory(category);
                row.setContent(objectMapper.writeValueAsString(config));
                row.setOperatorId(operatorId);
                row.setUpdateTime(LocalDateTime.now());
                sysIntroMapper.insert(row);
            } else {
                row.setContent(objectMapper.writeValueAsString(config));
                row.setOperatorId(operatorId);
                row.setUpdateTime(LocalDateTime.now());
                sysIntroMapper.updateById(row);
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("保存系统配置失败", e);
        }
    }
}
