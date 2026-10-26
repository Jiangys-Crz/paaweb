package com.cwnupaa.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.vo.UserProfileVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One policy for file reads, business references and deletion. Sensitive uses always win. */
@Service
public class FileAccessPolicy {
    @Autowired private FileSystemMapper fileSystemMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private UniversityMapper universityMapper;
    @Autowired private FileRelationMapper fileRelationMapper;
    @Autowired private SysIntroMapper sysIntroMapper;
    @Autowired private NoticeMapper noticeMapper;
    @Autowired private ContestCategoryMapper contestCategoryMapper;
    @Autowired private AssociationMemberMapper associationMemberMapper;
    @Autowired private CertificateTypeMapper certificateTypeMapper;
    @Autowired private CertificateVerifyMapper certificateVerifyMapper;
    @Autowired private CertificateIssueMapper certificateIssueMapper;
    @Autowired private AuditRecordsMapper auditRecordsMapper;
    @Autowired private SchoolAdminApplicationMapper schoolAdminApplicationMapper;
    @Autowired private TeamMapper teamMapper;
    @Autowired private EventMapper eventMapper;
    @Autowired private TrainingMapper trainingMapper;
    @Autowired private AdminPermissionsMapper adminPermissionsMapper;
    @Autowired private FileStorageService fileStorageService;
    @Autowired private PlatformTransactionManager transactionManager;

    private enum Purpose { GENERAL, IDENTITY, CERTIFICATE, TEMPLATE }
    private static final Pattern FILE_REFERENCE = Pattern.compile("/(?:api/)?file/(?:image|download)/(\\d+)(?![0-9])");
    private static final Pattern UPLOAD_REFERENCE = Pattern.compile("/(?:api/)?uploads/([^\\s\\\"'<>?#)]+)");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger LOGGER = LoggerFactory.getLogger(FileAccessPolicy.class);

    public boolean canAccess(FileSystem file, UserProfileVO user) {
        if (file == null || file.getFileId() == null) return false;
        Purpose purpose = purpose(file);
        if (purpose == Purpose.IDENTITY) {
            return owns(file, user) || hasPermission(user, "manage_user")
                    || (user != null && userMapper.selectCount(new QueryWrapper<Users>()
                        .eq("user_id", user.getUserId()).and(q -> q.eq("student_card_file_id", file.getFileId())
                            .or().eq("id_photo_file_id", file.getFileId()))) > 0);
        }
        if (purpose == Purpose.CERTIFICATE) {
            return owns(file, user) || hasPermission(user, "manage_cert_grant")
                    || (user != null && certificateVerifyMapper.selectCount(new QueryWrapper<CertificateVerify>()
                        .eq("file_id", file.getFileId()).eq("user_id", user.getUserId())) > 0);
        }
        if (purpose == Purpose.TEMPLATE) return hasPermission(user, "manage_cert_grant");
        return hasPublicReference(file) || owns(file, user);
    }

    /** Must be called before inserting any public file reference, within the writer's transaction. */
    public String validatePublicFile(Long fileId, UserProfileVO user, boolean imageOnly) {
        if (fileId == null) return null;
        FileSystem file = lockFile(fileId);
        if (file == null) return "引用的文件不存在";
        if (!owns(file, user) && !isRoot(user)) return "只能引用自己上传的文件";
        if (purpose(file) != Purpose.GENERAL) return "实名材料、证件照、证书材料和证书模板不能公开引用";
        if (imageOnly && !isImage(file)) return "只能引用图片文件";
        return null;
    }

    public String validatePrivateFile(Long fileId, UserProfileVO user, boolean imageOnly) {
        if (fileId == null) return null;
        FileSystem file = lockFile(fileId);
        if (file == null) return "引用的文件不存在";
        if (!owns(file, user) && !isRoot(user)) return "只能使用自己上传的文件";
        Purpose purpose = purpose(file);
        if (purpose == Purpose.CERTIFICATE || purpose == Purpose.TEMPLATE) return "该文件用途不适用于实名材料或证件照";
        // Explicit identity uploads are private from creation; public writers reject
        // this persisted purpose. Only legacy/general files need a publication scan.
        if (!Purpose.IDENTITY.name().equals(file.getAccessPurpose()) && hasPublicReference(file)) {
            return "已公开展示的文件不能作为私有实名材料，请重新上传";
        }
        if (imageOnly && !isImage(file)) return "只能引用图片文件";
        markPurpose(file, Purpose.IDENTITY);
        return null;
    }

    public String validateCertificateFile(Long fileId, UserProfileVO user, boolean template) {
        if (fileId == null) return "文件不能为空";
        FileSystem file = lockFile(fileId);
        if (file == null) return "引用的文件不存在";
        if (!owns(file, user) && !isRoot(user)) return "只能使用自己上传的文件";
        Purpose expected = template ? Purpose.TEMPLATE : Purpose.CERTIFICATE;
        Purpose current = purpose(file);
        if (current != Purpose.GENERAL && current != expected) return "实名材料、证书材料和证书模板不能混用";
        // Explicit certificate uploads are private from creation; public writers reject
        // their persisted purpose. Only legacy/general files need a publication scan.
        if (!expected.name().equals(file.getAccessPurpose()) && hasPublicReference(file)) {
            return "已公开展示的文件不能作为私有证书材料或模板，请重新上传";
        }
        if (template && (file.getType() == null || !"pdf".equalsIgnoreCase(file.getType().replace(".", "")))) return "证书模板必须为 PDF";
        if (!template && !isImage(file)) return "证书材料必须为图片";
        markPurpose(file, expected);
        return null;
    }

    private void markPurpose(FileSystem file, Purpose purpose) {
        if (!purpose.name().equals(file.getAccessPurpose())) {
            file.setAccessPurpose(purpose.name());
            fileSystemMapper.updateById(file);
        }
    }

    public String validatePublicFiles(Collection<Long> ids, UserProfileVO user) {
        if (ids == null) return null;
        // Stable lock order also avoids deadlocks when two publishers share multiple files.
        for (Long id : ids.stream().filter(Objects::nonNull).distinct().sorted().toList()) {
            String error = validatePublicFile(id, user, false);
            if (error != null) return error;
        }
        return null;
    }

    public String validatePublicContent(String content, UserProfileVO user) {
        if (!StringUtils.hasText(content)) return null;
        Set<Long> ids = new TreeSet<>();
        Matcher idMatcher = FILE_REFERENCE.matcher(content);
        while (idMatcher.find()) {
            try { ids.add(Long.parseLong(idMatcher.group(1))); }
            catch (NumberFormatException e) { return "文件引用不合法"; }
        }
        Matcher pathMatcher = UPLOAD_REFERENCE.matcher(content);
        while (pathMatcher.find()) {
            String path = pathMatcher.group(1);
            FileSystem file = fileSystemMapper.selectOne(new QueryWrapper<FileSystem>()
                    .and(q -> q.eq("url", "/uploads/" + path).or().eq("storage_key", path)).last("LIMIT 1"));
            if (file == null) return "引用的文件不存在";
            ids.add(file.getFileId());
        }
        return validatePublicFiles(ids, user);
    }

    @Transactional(rollbackFor = Exception.class)
    public String deleteOwnedUnreferenced(Long fileId, UserProfileVO user) throws Exception {
        FileSystem file = lockFile(fileId);
        if (file == null) return null;
        if (!owns(file, user) && !isRoot(user)) return "无权删除该文件";
        if (hasAnyReference(file)) return "文件仍被业务引用，请先解除引用";
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            if (fileSystemMapper.deleteById(fileId) != 1) throw new IllegalStateException("文件记录删除失败");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try { fileStorageService.delete(file); }
                    catch (Exception e) {
                        // Only metadata is retained for cleanup; never log file contents, credentials or exception messages.
                        String key = file.getStorageKey() == null ? file.getUrl() : file.getStorageKey();
                        if (key != null) key = key.replaceAll("[\\r\\n]", "");
                        LOGGER.warn("File cleanup pending: fileId={} provider={} key={} failureType={}",
                                fileId, file.getStorageType(), key, e.getClass().getSimpleName());
                    }
                }
            });
        } else {
            fileStorageService.delete(file);
            fileSystemMapper.deleteById(fileId);
        }
        return null;
    }

    /** Cleanup after a business reference was removed. Shared and other users' files are retained. */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteIfUnreferenced(Long fileId, UserProfileVO user) {
        if (fileId == null) return false;
        try { return deleteOwnedUnreferenced(fileId, user) == null; }
        catch (Exception e) { return false; }
    }

    /** Optional cleanup must not abort the business write or run before it commits. */
    public void deleteIfUnreferencedAfterCommit(Long fileId, UserProfileVO user) {
        if (fileId == null) return;
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanupInNewTransaction(fileId, user);
                }
            });
        } else {
            cleanupInNewTransaction(fileId, user);
        }
    }

    private void cleanupInNewTransaction(Long fileId, UserProfileVO user) {
        TransactionTemplate cleanup = new TransactionTemplate(transactionManager);
        cleanup.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            cleanup.executeWithoutResult(status -> {
                try { deleteOwnedUnreferenced(fileId, user); }
                catch (Exception e) { throw new IllegalStateException("文件清理失败", e); }
            });
        } catch (RuntimeException e) {
            LOGGER.warn("File cleanup pending: fileId={} failureType={}", fileId, e.getClass().getSimpleName());
        }
    }

    public boolean hasPermission(UserProfileVO actor, String permission) {
        if (actor == null || actor.getUserId() == null) return false;
        Users user = userMapper.selectById(actor.getUserId());
        if (user == null || !"normal".equals(user.getAccountStatus())) return false;
        if (rootRole(user.getRole())) return true;
        AdminPermissions row = adminPermissionsMapper.selectOne(new QueryWrapper<AdminPermissions>().eq("user_id", user.getUserId()));
        if (row == null || !StringUtils.hasText(row.getPermissions())) return false;
        try { return JSON.readValue(row.getPermissions(), new TypeReference<List<String>>() {}).contains(permission); }
        catch (Exception e) { return false; }
    }

    public boolean isRoot(UserProfileVO actor) {
        if (actor == null || actor.getUserId() == null) return false;
        Users user = userMapper.selectById(actor.getUserId());
        return user != null && "normal".equals(user.getAccountStatus()) && rootRole(user.getRole());
    }

    private static boolean rootRole(String role) { return "super_admin".equals(role) || "supper_admin".equals(role); }
    private static boolean owns(FileSystem file, UserProfileVO user) {
        return user != null && user.getUserId() != null && user.getUserId().equals(file.getUploaderId());
    }
    private static boolean isImage(FileSystem file) {
        return file.getType() != null && Set.of("png", "jpg", "jpeg", "gif").contains(file.getType().replace(".", "").toLowerCase(Locale.ROOT));
    }
    private FileSystem lockFile(Long id) {
        return id == null ? null : fileSystemMapper.selectOne(new QueryWrapper<FileSystem>().eq("file_id", id).last("FOR UPDATE"));
    }

    private Purpose purpose(FileSystem file) {
        Long id = file.getFileId();
        String key = Optional.ofNullable(file.getStorageKey()).orElse(Optional.ofNullable(file.getUrl()).orElse(""));
        if (Purpose.IDENTITY.name().equals(file.getAccessPurpose()) || key.contains("sensitive/id_photo/") || key.contains("sensitive/student_card/") || key.contains("id-photos/")
                || userMapper.selectCount(new QueryWrapper<Users>().and(q -> q.eq("student_card_file_id", id).or().eq("id_photo_file_id", id))) > 0
                || auditRecordsMapper.selectCount(new QueryWrapper<AuditRecords>().eq("file_id", id).eq("biz_type", "user_auth")) > 0
                || schoolAdminApplicationMapper.selectCount(new QueryWrapper<SchoolAdminApplication>().eq("file_id", id)) > 0) return Purpose.IDENTITY;
        if (Purpose.CERTIFICATE.name().equals(file.getAccessPurpose()) || key.contains("sensitive/certificate/") || certificateVerifyMapper.selectCount(new QueryWrapper<CertificateVerify>().eq("file_id", id)) > 0
                || auditRecordsMapper.selectCount(new QueryWrapper<AuditRecords>().eq("file_id", id).eq("biz_type", "certificate")) > 0) return Purpose.CERTIFICATE;
        if (Purpose.TEMPLATE.name().equals(file.getAccessPurpose()) || key.contains("sensitive/certificate_template/") || certificateTypeMapper.selectCount(new QueryWrapper<CertificateType>().eq("url_id", id)) > 0
                || certificateIssueMapper.selectCount(new QueryWrapper<CertificateIssue>().eq("template_file_id", id)) > 0) return Purpose.TEMPLATE;
        return Purpose.GENERAL;
    }

    private boolean hasPublicReference(FileSystem file) {
        Long id = file.getFileId();
        return userMapper.selectCount(new QueryWrapper<Users>().and(q -> q.eq("avatar_file_id", id).or().eq("background_file_id", id))) > 0
                || universityMapper.selectCount(new QueryWrapper<University>().eq("logo_file_id", id)) > 0
                || fileRelationMapper.selectCount(new QueryWrapper<FileRelation>().eq("file_id", id).in("biz_type", "notice", "event", "training")) > 0
                || contestCategoryMapper.selectCount(new QueryWrapper<ContestCategory>().eq("logo_file_id", id)) > 0
                || noticeMapper.selectCount(new QueryWrapper<Notice>().eq("cover_file_id", id)) > 0
                || associationMemberMapper.selectCount(new QueryWrapper<AssociationMember>().eq("avatar_file_id", id)) > 0
                || teamMapper.selectCount(new QueryWrapper<Team>().eq("team_image_id", id)) > 0 || hasContentReference(file);
    }

    private boolean hasAnyReference(FileSystem file) {
        Long id = file.getFileId();
        return userMapper.selectCount(new QueryWrapper<Users>().and(q -> q.eq("student_card_file_id", id).or().eq("id_photo_file_id", id))) > 0
                    || certificateVerifyMapper.selectCount(new QueryWrapper<CertificateVerify>().eq("file_id", id)) > 0
                    || certificateTypeMapper.selectCount(new QueryWrapper<CertificateType>().eq("url_id", id)) > 0
                    || certificateIssueMapper.selectCount(new QueryWrapper<CertificateIssue>().eq("template_file_id", id)) > 0
                    || schoolAdminApplicationMapper.selectCount(new QueryWrapper<SchoolAdminApplication>().eq("file_id", id)) > 0
                    || auditRecordsMapper.selectCount(new QueryWrapper<AuditRecords>().eq("file_id", id)) > 0
                || fileRelationMapper.selectCount(new QueryWrapper<FileRelation>().eq("file_id", id)) > 0
                || hasPublicReference(file);
    }

    private boolean hasContentReference(FileSystem file) {
        return safe(sysIntroMapper.selectList(new QueryWrapper<SysIntro>().select("content").notIn("category", "smtp_config", "object_storage_config"))).stream().anyMatch(x -> contains(x.getContent(), file))
                || safe(noticeMapper.selectList(new QueryWrapper<Notice>().select("content"))).stream().anyMatch(x -> contains(x.getContent(), file))
                || safe(eventMapper.selectList(new QueryWrapper<Event>().select("description"))).stream().anyMatch(x -> contains(x.getDescription(), file))
                || safe(trainingMapper.selectList(new QueryWrapper<Training>().select("description"))).stream().anyMatch(x -> contains(x.getDescription(), file))
                || safe(contestCategoryMapper.selectList(new QueryWrapper<ContestCategory>().select("intro"))).stream().anyMatch(x -> contains(x.getIntro(), file))
                || safe(associationMemberMapper.selectList(new QueryWrapper<AssociationMember>().select("introduction"))).stream().anyMatch(x -> contains(x.getIntroduction(), file))
                || safe(teamMapper.selectList(new QueryWrapper<Team>().select("introduce"))).stream().anyMatch(x -> contains(x.getIntroduce(), file));
    }
    // MyBatis may map a projection containing only NULL columns to a null row.
    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }
    private static boolean contains(String content, FileSystem file) {
        if (!StringUtils.hasText(content)) return false;
        Matcher matcher = FILE_REFERENCE.matcher(content);
        while (matcher.find()) if (matcher.group(1).equals(String.valueOf(file.getFileId()))) return true;
        String key = file.getStorageKey();
        if (!StringUtils.hasText(key) && file.getUrl() != null && file.getUrl().startsWith("/uploads/")) key = file.getUrl().substring(9);
        return StringUtils.hasText(key) && content.contains("/uploads/" + key);
    }
}
