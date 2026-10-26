package com.cwnupaa.backend.config;

import com.cwnupaa.backend.common.GlobalExceptionHandler;
import com.cwnupaa.backend.common.Result;
import jakarta.servlet.MultipartConfigElement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Uses real HTTP and Tomcat multipart parsing; MockMvc multipart requests bypass it. */
class MultipartUploadHttpTests {
    @TempDir Path uploads;
    private AnnotationConfigServletWebServerApplicationContext context;
    private URI base;

    @BeforeEach
    void start() {
        context = new AnnotationConfigServletWebServerApplicationContext();
        TestPropertyValues.of("file.upload.path=" + uploads,
                "spring.servlet.multipart.max-file-size=1KB", "spring.servlet.multipart.max-request-size=2KB")
                .applyTo(context);
        context.register(Config.class, MultipartStorageConfig.class);
        context.refresh();
        base = URI.create("http://127.0.0.1:" + context.getWebServer().getPort());
    }

    @AfterEach
    void stop() { if (context != null) context.close(); }

    @ParameterizedTest
    @ValueSource(strings = {"/file/upload", "/certificate/grant/import/preview", "/admin/bulk-email/parse"})
    void oversizedFileReturnsJson413ForEveryMultipartEntry(String endpoint) throws Exception {
        HttpResponse<String> response = send(endpoint, multipart("x".repeat(1025)), "multipart/form-data; boundary=test-boundary");
        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("\"code\":413", "上传文件过大");
    }

    @Test
    void smallFileUsesAnExistingPersistentDirectoryAndTheTemporaryPartIsCleaned() throws Exception {
        assertThat(context.getBean(MultipartConfigElement.class).getLocation()).isEqualTo(uploads.resolve(".multipart").toString());
        assertThat(Files.isDirectory(uploads.resolve(".multipart"))).isTrue();
        HttpResponse<String> response = send("/file/upload", multipart("hello"), "multipart/form-data; boundary=test-boundary");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"data\":5");
        try (var files = Files.list(uploads.resolve(".multipart"))) { assertThat(files.count()).isZero(); }
    }

    @Test
    void oversizedRequestAndMissingBoundaryHaveUsefulErrors() throws Exception {
        HttpResponse<String> large = send("/file/upload", multipart("x".repeat(3000)), "multipart/form-data; boundary=test-boundary");
        assertThat(large.statusCode()).isEqualTo(413);
        HttpResponse<String> malformed = send("/file/upload", "not-a-multipart-body", "multipart/form-data");
        assertThat(malformed.statusCode()).isEqualTo(400);
        assertThat(malformed.body()).contains("上传请求格式无效");
    }

    private String multipart(String content) {
        return "--test-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"test.txt\"\r\n"
                + "Content-Type: text/plain\r\n\r\n" + content + "\r\n--test-boundary--\r\n";
    }

    private HttpResponse<String> send(String endpoint, String body, String contentType) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(base.resolve(endpoint)).header("Content-Type", contentType)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class Config {
        @Bean static PropertySourcesPlaceholderConfigurer placeholders() { return new PropertySourcesPlaceholderConfigurer(); }
        @Bean TomcatServletWebServerFactory server() { return new TomcatServletWebServerFactory(0); }
        @Bean DispatcherServlet dispatcherServlet() { return new DispatcherServlet(); }
        @Bean ServletRegistrationBean<DispatcherServlet> servlet(DispatcherServlet servlet, MultipartConfigElement config) {
            var registration = new ServletRegistrationBean<>(servlet, "/");
            registration.setMultipartConfig(config);
            return registration;
        }
        @Bean StandardServletMultipartResolver multipartResolver() { return new StandardServletMultipartResolver(); }
        @Bean GlobalExceptionHandler advice() { return new GlobalExceptionHandler(); }
        @Bean UploadEndpoint endpoint() { return new UploadEndpoint(); }
    }

    @RestController
    static class UploadEndpoint {
        @PostMapping({"/file/upload", "/certificate/grant/import/preview", "/admin/bulk-email/parse"})
        Result<Long> upload(@RequestParam("file") MultipartFile file) { return Result.success(file.getSize()); }
    }
}
