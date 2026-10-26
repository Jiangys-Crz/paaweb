package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.entity.FileSystem;
import com.cwnupaa.backend.mapper.FileSystemMapper;
import com.cwnupaa.backend.service.FileStorageService;
import com.cwnupaa.backend.service.FileUploadService;
import com.cwnupaa.backend.service.FileAccessPolicy;
import com.cwnupaa.backend.utils.SecurityUtils;
import com.cwnupaa.backend.vo.UserProfileVO;
import jakarta.servlet.http.HttpServletRequest;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.dao.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/file")
public class FileController {
    private static final Logger log = LoggerFactory.getLogger(FileController.class);
    private static final String ACCEL_SUPPORTED_HEADER = "X-Accel-Supported";
    private static final String ACCEL_REDIRECT_HEADER = "X-Accel-Redirect";

    @Autowired
    private FileSystemMapper fileSystemMapper;
    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    private FileUploadService fileUploadService;
    @Autowired
    private FileAccessPolicy fileAccessPolicy;

    private static final List<String> IMAGE_EXTENSIONS = Arrays.asList(".jpg", ".jpeg", ".png");
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".zip", ".txt");
    private static final long MAX_UPLOAD_SIZE = 20L * 1024 * 1024;
    private static final long MAX_IMAGE_UPLOAD_SIZE = 5L * 1024 * 1024;
    private static final long MAX_SPREADSHEET_UPLOAD_SIZE = 10L * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 25_000_000L;
    private static final int MAX_WIDTH = 1920;
    private static final float OUTPUT_QUALITY = 0.8f;

    @PostMapping("/upload")
    public Result<Long> upload(@RequestParam("file") MultipartFile file,
                               @RequestParam(required = false) Long uploaderId,
                               @RequestParam(required = false) String uploadType,
                               HttpServletRequest request) {
        Long currentUserId = SecurityUtils.currentUserId(request);
        if (currentUserId == null) return Result.error(401, "未登录或会话已失效");
        if (file.isEmpty()) return Result.error(400, "文件为空");

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank() || originalFilename.codePointCount(0, originalFilename.length()) > 255) {
            return Result.error(400, "文件名不能为空且不能超过255个字符，请重命名后上传");
        }
        String suffix = originalFilename != null && originalFilename.contains(".") ?
                originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_EXTENSIONS.contains(suffix)) {
            return Result.error(400, "不支持的文件类型");
        }
        long sizeLimit = uploadSizeLimit(suffix);
        if (file.getSize() > sizeLimit) {
            return Result.error(413, "该类型文件大小不能超过" + (sizeLimit / 1024 / 1024) + "MB");
        }

        if ("certificate".equals(uploadType)) {
            if (!IMAGE_EXTENSIONS.contains(suffix)) {
                return Result.error(400, "只能上传 PNG/JPG/JPEG 格式的图片文件");
            }
        }
        if ("id_photo".equals(uploadType)) {
            if (!IMAGE_EXTENSIONS.contains(suffix)) {
                return Result.error(400, "证件照只能上传 PNG/JPG/JPEG 格式的图片文件");
            }
        }
        if ("student_card".equals(uploadType) && !IMAGE_EXTENSIONS.contains(suffix)) {
            return Result.error(400, "认证照片只能上传 PNG/JPG/JPEG 格式的图片文件");
        }
        if ("school_logo".equals(uploadType) && !IMAGE_EXTENSIONS.contains(suffix)) {
            return Result.error(400, "学校 LOGO 只能上传 PNG/JPG/JPEG 格式的图片文件");
        }
        if ("certificate_template".equals(uploadType)) {
            if (!".pdf".equals(suffix)) {
                return Result.error(400, "证书模板只能上传 PDF 文件");
            }
            try (InputStream input = file.getInputStream(); PDDocument document = PDDocument.load(input)) {
                if (document.getNumberOfPages() != 1) {
                    return Result.error(400, "证书模板 PDF 必须且只能包含 1 页");
                }
            } catch (IOException e) {
                return Result.error(400, "无法读取证书模板 PDF，请检查文件是否损坏或加密");
            }
        }
        if (IMAGE_EXTENSIONS.contains(suffix)) {
            try {
                if (!hasSafeImageDimensions(file)) {
                    return Result.error(400, "图片无效或像素过大，最大支持 2500 万像素");
                }
            } catch (IOException e) {
                return Result.error(400, "无法读取图片文件");
            }
        }

        String newName = UUID.randomUUID() + suffix;
        Path temp = null;
        FileStorageService.StoredFile stored = null;

        try {
            temp = Files.createTempFile("cwnupaa-upload-", suffix);
            if (IMAGE_EXTENSIONS.contains(suffix)) {
                try (InputStream input = file.getInputStream()) {
                    BufferedImage originalImage = ImageIO.read(input);
                    if (originalImage == null) {
                        file.transferTo(temp);
                    } else {
                        Thumbnails.Builder<BufferedImage> builder = Thumbnails.of(originalImage);

                        if (originalImage.getWidth() > MAX_WIDTH) {
                            builder.width(MAX_WIDTH);
                        } else {
                            builder.scale(1.0f);
                        }

                        builder.outputQuality(OUTPUT_QUALITY)
                                .toFile(temp.toFile());
                    }
                } catch (Exception e) {
                    log.warn("图片压缩失败，保留原始文件：userId={} purpose={} ({})", currentUserId, uploadType, e.getClass().getSimpleName());
                    file.transferTo(temp);
                }
            } else {
                file.transferTo(temp);
            }

            stored = fileStorageService.store(
                    temp, newName, uploadType, file.getContentType());
            return Result.success(fileUploadService.register(stored, originalFilename, suffix.substring(1), uploadType, currentUserId));
        } catch (Exception e) {
            log.error("文件上传失败：userId={} purpose={} size={} ({})", currentUserId, uploadType, file.getSize(), e.getClass().getSimpleName());
            if (stored != null) {
                FileSystem failedFile = new FileSystem();
                failedFile.setStorageType(stored.storageType());
                failedFile.setStorageKey(stored.storageKey());
                try { fileStorageService.delete(failedFile); }
                catch (Exception cleanupError) { log.warn("上传失败后的文件清理失败：key={} ({})", stored.storageKey(), cleanupError.getClass().getSimpleName()); }
            }
            String message = e instanceof DataAccessException
                    ? "上传文件记录保存失败，请管理员检查文件表迁移和后端日志后重试"
                    : "文件上传失败，请稍后重试或联系管理员";
            return Result.error(500, message);
        } finally {
            if (temp != null) {
                try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            }
        }
    }

    @DeleteMapping("/delete/{fileId}")
    public Result<String> delete(@PathVariable Long fileId, HttpServletRequest request) {
        UserProfileVO user = SecurityUtils.currentUser(request);
        if (user == null) return Result.error("未登录或会话已失效");
        try {
            String error = fileAccessPolicy.deleteOwnedUnreferenced(fileId, user);
            return error == null ? Result.success("删除成功") : Result.error(error);
        } catch (Exception e) {
            return Result.error("删除失败");
        }
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<?> download(@PathVariable Long fileId, HttpServletRequest request) throws IOException {
        FileSystem fs = fileSystemMapper.selectById(fileId);
        if (fs == null) return ResponseEntity.notFound().build();
        if (!canAccess(fs, SecurityUtils.currentUser(request))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.TEXT_PLAIN).body("无权限访问该文件");
        }

        String encodedName = URLEncoder.encode(fs.getName(), StandardCharsets.UTF_8).replace("+", "%20");
        String contentDisposition = "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName;
        String internalUri = fileStorageService.internalRedirectUri(fs, true);
        if ("1".equals(request.getHeader(ACCEL_SUPPORTED_HEADER)) && internalUri != null) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .header(ACCEL_REDIRECT_HEADER, internalUri)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .build();
        }

        Resource resource;
        try { resource = fileStorageService.readResource(fs); }
        catch (Exception e) { return ResponseEntity.internalServerError().body("文件读取失败"); }
        if (resource == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .contentLength(resource.contentLength())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @GetMapping("/image/{fileId}")
    public ResponseEntity<?> getImage(@PathVariable Long fileId, HttpServletRequest request) throws IOException {
        FileSystem fs = fileSystemMapper.selectById(fileId);
        if (fs == null) return ResponseEntity.notFound().build();
        if (!canAccess(fs, SecurityUtils.currentUser(request))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.TEXT_PLAIN).body("无权限访问该文件");
        }

        String mimeType = "image/jpeg";
        if (fs.getType().equalsIgnoreCase(".png") || fs.getType().equalsIgnoreCase("png")) mimeType = "image/png";
        if (fs.getType().equalsIgnoreCase(".gif") || fs.getType().equalsIgnoreCase("gif")) mimeType = "image/gif";

        String internalUri = fileStorageService.internalRedirectUri(fs, false);
        if ("1".equals(request.getHeader(ACCEL_SUPPORTED_HEADER)) && internalUri != null) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fs.getName() + "\"")
                    .header(ACCEL_REDIRECT_HEADER, internalUri)
                    .contentType(MediaType.parseMediaType(mimeType))
                    .build();
        }

        Resource resource;
        try { resource = fileStorageService.readResource(fs); }
        catch (Exception e) { return ResponseEntity.internalServerError().body("文件读取失败"); }
        if (resource == null) return ResponseEntity.notFound().build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fs.getName() + "\"")
                .contentLength(resource.contentLength())
                .contentType(MediaType.parseMediaType(mimeType))
                .body(resource);
    }

    private long uploadSizeLimit(String suffix) {
        if (IMAGE_EXTENSIONS.contains(suffix) || ".gif".equals(suffix)) return MAX_IMAGE_UPLOAD_SIZE;
        if (".xls".equals(suffix) || ".xlsx".equals(suffix)) return MAX_SPREADSHEET_UPLOAD_SIZE;
        return MAX_UPLOAD_SIZE;
    }

    private boolean hasSafeImageDimensions(MultipartFile file) throws IOException {
        try (InputStream source = file.getInputStream(); ImageInputStream input = ImageIO.createImageInputStream(source)) {
            if (input == null) return false;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return false;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                return width > 0 && height > 0 && (long) width * height <= MAX_IMAGE_PIXELS;
            } finally {
                reader.dispose();
            }
        }
    }

    /** Shared with legacy /uploads paths; sensitive references are evaluated first. */
    public boolean canAccess(FileSystem fs, UserProfileVO user) {
        return fileAccessPolicy.canAccess(fs, user);
    }
}
