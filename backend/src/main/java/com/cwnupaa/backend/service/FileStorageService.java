package com.cwnupaa.backend.service;

import com.cwnupaa.backend.dto.ObjectStorageConfigDTO;
import com.cwnupaa.backend.entity.FileSystem;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDate;
import java.util.Set;
import java.util.StringJoiner;

@Service
public class FileStorageService {
    public static final String LOCAL = "LOCAL";
    public static final String OBJECT = "OBJECT";

    /**
     * 网关容器里的 Nginx 以非 root 的 nginx 用户读取挂载进来的 uploads 目录。
     * 落盘的文件和目录都必须对"其他用户"可读/可进入，否则 Nginx 直发时会返回 403。
     * JDK 的 {@code Files.createTempFile} 默认创建 0600 文件，且 {@code Files.move}
     * 会把该权限带到目标文件，所以写入后必须显式放开。
     */
    private static final Set<PosixFilePermission> GATEWAY_FILE_PERMISSIONS =
            PosixFilePermissions.fromString("rw-r--r--");
    private static final Set<PosixFilePermission> GATEWAY_DIR_PERMISSIONS =
            PosixFilePermissions.fromString("rwxr-xr-x");

    private final SystemConfigService systemConfigService;

    @Value("${file.upload.path}")
    private String uploadPath;

    public FileStorageService(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    public StoredFile store(Path source, String generatedName, boolean privateFile, String contentType) throws Exception {
        return store(source, generatedName, privateFile ? "id_photo" : null, contentType);
    }

    public StoredFile store(Path source, String generatedName, String purpose, String contentType) throws Exception {
        String sensitivePurpose = Set.of("id_photo", "student_card", "certificate", "certificate_template").contains(purpose == null ? "" : purpose)
                ? purpose : null;
        ObjectStorageConfigDTO config = systemConfigService.getObjectStorageConfig();
        if (config.isEnabled()) {
            systemConfigService.validateStorage(config);
            String key = buildObjectKey(config.getPrefix(), sensitivePurpose, generatedName);
            try (S3Client client = createClient(config)) {
                PutObjectRequest.Builder request = PutObjectRequest.builder().bucket(config.getBucket()).key(key);
                if (StringUtils.hasText(contentType)) request.contentType(contentType);
                client.putObject(request.build(), RequestBody.fromFile(source));
            }
            return new StoredFile(OBJECT, key);
        }

        String key = sensitivePurpose == null ? generatedName : "sensitive/" + sensitivePurpose + "/" + generatedName;
        Path target = localPath(key);
        Files.createDirectories(target.getParent());
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        // 见 GATEWAY_FILE_PERMISSIONS：不放开权限会让 Nginx 直发 403（附件无法下载）。
        makeReadableByGateway(target);
        return new StoredFile(LOCAL, key);
    }

    public byte[] readBytes(FileSystem file) throws Exception {
        if (isObject(file)) {
            ObjectStorageConfigDTO config = systemConfigService.getObjectStorageConfig();
            systemConfigService.validateStorageForRead(config);
            try (S3Client client = createClient(config)) {
                return client.getObjectAsBytes(GetObjectRequest.builder()
                        .bucket(config.getBucket()).key(storageKey(file)).build()).asByteArray();
            }
        }
        Path path = localPath(storageKey(file));
        return Files.exists(path) ? Files.readAllBytes(path) : null;
    }

    /** 本地文件和对象存储都以流响应，避免下载时把整个文件复制到 JVM 堆内存。 */
    public Resource readResource(FileSystem file) throws Exception {
        if (file == null) return null;
        if (!isObject(file)) {
            Path path = localPath(storageKey(file));
            return Files.isRegularFile(path) ? new FileSystemResource(path) : null;
        }

        ObjectStorageConfigDTO config = systemConfigService.getObjectStorageConfig();
        systemConfigService.validateStorageForRead(config);
        S3Client client = createClient(config);
        try {
            ResponseInputStream<GetObjectResponse> objectStream = client.getObject(GetObjectRequest.builder()
                    .bucket(config.getBucket()).key(storageKey(file)).build());
            long contentLength = objectStream.response().contentLength();
            FilterInputStream managedStream = new FilterInputStream(objectStream) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        client.close();
                    }
                }
            };
            return new InputStreamResource(managedStream, file.getName()) {
                @Override
                public long contentLength() {
                    return contentLength;
                }
            };
        } catch (Exception e) {
            client.close();
            throw e;
        }
    }

    /** 生成仅供 Nginx internal location 使用的本地文件 URI。 */
    public String internalRedirectUri(FileSystem file, boolean download) {
        if (file == null || isObject(file)) return null;
        Path base = Path.of(uploadPath).toAbsolutePath().normalize();
        Path path = localPath(storageKey(file));
        if (!Files.isRegularFile(path)) return null;
        // 2026-09 之前落盘的文件可能仍是 0600（JDK 临时文件权限被 Files.move 带过来），
        // 网关的 Nginx 读不到就会返回 403。这里就地修正；修正不了就返回 null，
        // 让后端自己流式返回文件——慢一点，但用户至少能下载到。
        if (!ensureReadableByGateway(base, path)) return null;

        StringJoiner encodedPath = new StringJoiner("/");
        for (Path segment : base.relativize(path)) {
            encodedPath.add(UriUtils.encodePathSegment(segment.toString(), StandardCharsets.UTF_8));
        }
        String prefix = download ? "/_protected_downloads/" : "/_protected_media/";
        return prefix + encodedPath;
    }

    public File materialize(FileSystem file) throws Exception {
        if (!isObject(file)) return localPath(storageKey(file)).toFile();
        String suffix = file.getName() != null && file.getName().contains(".")
                ? file.getName().substring(file.getName().lastIndexOf('.')) : ".tmp";
        Path temp = Files.createTempFile("cwnupaa-object-", suffix);
        Files.write(temp, readBytes(file));
        temp.toFile().deleteOnExit();
        return temp.toFile();
    }

    public void delete(FileSystem file) throws Exception {
        if (isObject(file)) {
            ObjectStorageConfigDTO config = systemConfigService.getObjectStorageConfig();
            systemConfigService.validateStorageForRead(config);
            try (S3Client client = createClient(config)) {
                client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(config.getBucket()).key(storageKey(file)).build());
            }
        } else {
            Files.deleteIfExists(localPath(storageKey(file)));
        }
    }

    public void test(ObjectStorageConfigDTO incoming) {
        ObjectStorageConfigDTO config = systemConfigService.mergeStorageSecret(incoming);
        config.setEnabled(true);
        systemConfigService.validateStorage(config);
        try (S3Client client = createClient(config)) {
            client.headBucket(HeadBucketRequest.builder().bucket(config.getBucket()).build());
        }
    }

    public boolean isObject(FileSystem file) {
        return file != null && OBJECT.equalsIgnoreCase(file.getStorageType());
    }

    public String publicUrl(FileSystem file) {
        if (file == null) return null;
        return isObject(file) ? "/file/image/" + file.getFileId() : file.getUrl();
    }

    private S3Client createClient(ObjectStorageConfigDTO config) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(config.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(config.isPathStyleAccess()).build());
        if (StringUtils.hasText(config.getEndpoint())) builder.endpointOverride(URI.create(config.getEndpoint()));
        return builder.build();
    }

    private String buildObjectKey(String prefix, String purpose, String generatedName) {
        String safePrefix = StringUtils.hasText(prefix)
                ? prefix.trim().replaceAll("^/+|/+$", "").replace("..", "") + "/" : "";
        LocalDate now = LocalDate.now();
        return safePrefix + (purpose == null ? "uploads/" : "sensitive/" + purpose + "/")
                + now.getYear() + "/" + String.format("%02d", now.getMonthValue()) + "/" + generatedName;
    }

    private String storageKey(FileSystem file) {
        if (StringUtils.hasText(file.getStorageKey())) return file.getStorageKey();
        String url = file.getUrl() == null ? "" : file.getUrl();
        if (url.startsWith("/api")) url = url.substring(4);
        if (url.startsWith("/uploads/")) return url.substring("/uploads/".length());
        if (url.startsWith("/")) return url.substring(1);
        return url;
    }

    /**
     * 把落盘文件及其父目录放开到"网关可读"（0644 / 0755）。
     * POSIX 文件系统以外（例如 Windows 挂载）无法判断权限，按可读处理。
     */
    static void makeReadableByGateway(Path target) {
        try {
            Files.setPosixFilePermissions(target, GATEWAY_FILE_PERMISSIONS);
            Path parent = target.getParent();
            if (parent != null) Files.setPosixFilePermissions(parent, GATEWAY_DIR_PERMISSIONS);
        } catch (UnsupportedOperationException | IOException ignored) {
            // 非 POSIX 文件系统或目录由宿主机管理时忽略，读取侧还有流式回退兜底。
        }
    }

    /**
     * 确保网关容器的 Nginx 进程能读到该文件：文件需要 others-read，
     * 从 uploads 根到文件之间的每一级目录都需要 others-execute。
     *
     * @return true 表示可以直接交给 Nginx 直发；false 表示无法修正，调用方应回退到后端流式返回。
     */
    static boolean ensureReadableByGateway(Path base, Path file) {
        try {
            PosixFileAttributes attributes = Files.readAttributes(file, PosixFileAttributes.class);
            if (!attributes.permissions().contains(PosixFilePermission.OTHERS_READ)) {
                Files.setPosixFilePermissions(file, GATEWAY_FILE_PERMISSIONS);
            }
        } catch (UnsupportedOperationException e) {
            return true;
        } catch (IOException e) {
            return false;
        }

        Path dir = file.getParent();
        while (dir != null && dir.startsWith(base)) {
            try {
                PosixFileAttributes attributes = Files.readAttributes(dir, PosixFileAttributes.class);
                if (!attributes.permissions().contains(PosixFilePermission.OTHERS_EXECUTE)) {
                    Files.setPosixFilePermissions(dir, GATEWAY_DIR_PERMISSIONS);
                }
            } catch (UnsupportedOperationException e) {
                return true;
            } catch (IOException e) {
                return false;
            }
            if (dir.equals(base)) break;
            dir = dir.getParent();
        }
        return true;
    }

    private Path localPath(String key) {
        Path base = Path.of(uploadPath).toAbsolutePath().normalize();
        Path result = base.resolve(key).normalize();
        if (!result.startsWith(base)) throw new IllegalArgumentException("非法文件路径");
        return result;
    }

    public record StoredFile(String storageType, String storageKey) {}
}
