package com.cwnupaa.backend.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MultipartProperties.class)
@ConditionalOnProperty(prefix = "spring.servlet.multipart", name = "enabled", matchIfMissing = true)
public class MultipartStorageConfig {
    @Bean
    public MultipartConfigElement multipartConfigElement(MultipartProperties properties,
                                                         @Value("${file.upload.path}") String uploadPath) throws IOException {
        // Tomcat's default work directory can be removed by /tmp cleanup while
        // the app is running. Keep multipart staging under the mounted uploads.
        Path directory = StringUtils.hasText(properties.getLocation())
                ? Path.of(properties.getLocation()) : Path.of(uploadPath).resolve(".multipart");
        directory = directory.toAbsolutePath().normalize();
        Files.createDirectories(directory);
        if (!Files.isWritable(directory)) throw new IOException("上传临时目录不可写：" + directory);
        properties.setLocation(directory.toString());
        return properties.createMultipartConfig();
    }
}
