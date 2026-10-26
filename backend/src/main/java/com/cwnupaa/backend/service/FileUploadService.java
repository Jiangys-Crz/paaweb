package com.cwnupaa.backend.service;

import com.cwnupaa.backend.entity.FileSystem;
import com.cwnupaa.backend.mapper.FileSystemMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
public class FileUploadService {
    private static final Set<String> PRIVATE_PURPOSES =
            Set.of("id_photo", "student_card", "certificate", "certificate_template");
    private final FileSystemMapper fileSystemMapper;

    public FileUploadService(FileSystemMapper fileSystemMapper) {
        this.fileSystemMapper = fileSystemMapper;
    }

    /** Insert and final URL binding must either both commit or both roll back. */
    @Transactional(rollbackFor = Exception.class)
    public Long register(FileStorageService.StoredFile stored, String name, String type,
                         String uploadType, Long uploaderId) {
        boolean protectedUrl = PRIVATE_PURPOSES.contains(uploadType == null ? "" : uploadType)
                || FileStorageService.OBJECT.equals(stored.storageType());
        FileSystem file = new FileSystem();
        file.setName(name);
        file.setType(type);
        file.setStorageType(stored.storageType());
        file.setStorageKey(stored.storageKey());
        file.setAccessPurpose("certificate_template".equals(uploadType) ? "TEMPLATE"
                : "certificate".equals(uploadType) ? "CERTIFICATE"
                : ("id_photo".equals(uploadType) || "student_card".equals(uploadType)) ? "IDENTITY" : "GENERAL");
        // url is UNIQUE. A shared /file/image/0 collides with simultaneous uploads
        // and with records left behind when the old second write failed.
        file.setUrl(protectedUrl ? "/file/pending/" + UUID.randomUUID() : "/uploads/" + stored.storageKey());
        file.setUploaderId(uploaderId);
        file.setCreateTime(LocalDateTime.now());
        if (fileSystemMapper.insert(file) != 1 || file.getFileId() == null) {
            throw new IllegalStateException("文件记录创建失败");
        }
        if (protectedUrl) {
            file.setUrl("/file/image/" + file.getFileId());
            if (fileSystemMapper.updateById(file) != 1) throw new IllegalStateException("文件地址绑定失败");
        }
        return file.getFileId();
    }
}
