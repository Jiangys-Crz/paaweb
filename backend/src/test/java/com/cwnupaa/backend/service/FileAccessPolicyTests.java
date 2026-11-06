package com.cwnupaa.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.controller.NoticeController;
import com.cwnupaa.backend.dto.NoticeDTO;
import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.vo.UserProfileVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class FileAccessPolicyTests {
    private FileAccessPolicy policy;
    private FileSystem file;
    private FileSystemMapper files;
    private UserMapper users;
    private FileRelationMapper relations;
    private FileStorageService storage;
    private UserProfileVO owner;

    @BeforeEach
    void setUp() {
        policy = new FileAccessPolicy();
        for (Field field : FileAccessPolicy.class.getDeclaredFields()) {
            if (field.isAnnotationPresent(Autowired.class)) ReflectionTestUtils.setField(policy, field.getName(), mock(field.getType()));
        }
        files = dependency("fileSystemMapper");
        users = dependency("userMapper");
        relations = dependency("fileRelationMapper");
        storage = dependency("fileStorageService");
        owner = actor(7L);
        file = new FileSystem();
        file.setFileId(90L);
        file.setUploaderId(7L);
        file.setType("png");
        file.setStorageKey("ordinary.png");
        file.setUrl("/uploads/ordinary.png");
        when(files.selectOne(any())).thenReturn(file);
        when(files.deleteById(anyLong())).thenReturn(1);
    }

    @Test
    void sensitiveUploadCannotBecomePublicEvenBeforeItIsBoundToAUser() {
        for (String purpose : List.of("id_photo", "student_card", "certificate", "certificate_template")) {
            file.setStorageKey("sensitive/" + purpose + "/photo.png");
            when(relations.selectCount(any())).thenReturn(1L);
            assertThat(policy.canAccess(file, null)).isFalse();
            assertThat(policy.validatePublicFile(90L, owner, true)).contains("不能公开引用");
        }
    }

    @Test
    void historicalSensitiveReferencesWinOverPublicAttachments() {
        CertificateVerifyMapper certificates = dependency("certificateVerifyMapper");
        when(certificates.selectCount(any())).thenReturn(1L);
        when(relations.selectCount(any())).thenReturn(2L);
        assertThat(policy.canAccess(file, null)).isFalse();
        assertThat(policy.canAccess(file, owner)).isTrue();
        assertThat(policy.validatePublicFile(90L, owner, false)).contains("不能公开引用");
    }

    @Test
    void publicFilesStillAllowAnonymousReaders() {
        when(relations.selectCount(any())).thenReturn(1L);
        assertThat(policy.canAccess(file, null)).isTrue();
        assertThat(policy.validatePublicFile(90L, owner, true)).isNull();
    }

    @Test
    void collegeLogoIsPublicAndCannotBeDeletedWhileReferenced() throws Exception {
        SchoolMapper schools = dependency("schoolMapper");
        when(schools.selectCount(any())).thenReturn(1L);
        assertThat(policy.canAccess(file, null)).isTrue();
        assertThat(policy.deleteOwnedUnreferenced(90L, owner)).contains("仍被业务引用");
        verify(files, never()).deleteById(anyLong());
    }

    @Test
    void anEditorCannotPublishAnotherUploadByUsingItsNumericIdOrLegacyPath() {
        UserProfileVO other = actor(8L);
        assertThat(policy.validatePublicFiles(List.of(90L), other)).contains("自己上传");
        assertThat(policy.validatePublicContent("![image](/api/file/image/90)", other)).contains("自己上传");
        assertThat(policy.validatePublicContent("![image](/uploads/ordinary.png)", other)).contains("自己上传");
    }

    @Test
    void missingUploaderDoesNotGrantPublicationOrDeletionToAnOrdinaryAdministrator() throws Exception {
        file.setUploaderId(null);
        Users ordinaryAdmin = user(7L, "stu_admin", "normal");
        when(users.selectById(7L)).thenReturn(ordinaryAdmin);
        assertThat(policy.validatePublicFile(90L, owner, true)).contains("自己上传");
        assertThat(policy.deleteOwnedUnreferenced(90L, owner)).contains("无权删除");
        verify(storage, never()).delete(any());
    }

    @Test
    void sharedFileIsNotPhysicallyDeletedUntilAllReferencesAreGone() throws Exception {
        when(relations.selectCount(any())).thenReturn(1L);
        assertThat(policy.deleteOwnedUnreferenced(90L, owner)).contains("仍被业务引用");
        verify(storage, never()).delete(any());
        verify(files, never()).deleteById(anyLong());
        when(relations.selectCount(any())).thenReturn(0L);
        assertThat(policy.deleteOwnedUnreferenced(90L, owner)).isNull();
        verify(storage).delete(file);
        verify(files).deleteById(90L);
    }

    @Test
    void certificateIssueAndTextReferencesAlsoPreventDeletion() throws Exception {
        CertificateIssueMapper issues = dependency("certificateIssueMapper");
        when(issues.selectCount(any())).thenReturn(1L);
        assertThat(policy.deleteOwnedUnreferenced(90L, owner)).contains("仍被业务引用");
        when(issues.selectCount(any())).thenReturn(0L);
        SysIntro intro = new SysIntro();
        intro.setContent("![image](/api/file/image/90)");
        SysIntroMapper intros = dependency("sysIntroMapper");
        when(intros.selectList(any())).thenReturn(List.of(intro));
        assertThat(policy.deleteOwnedUnreferenced(90L, owner)).contains("仍被业务引用");
        verify(storage, never()).delete(any());
    }

    @Test
    void publicImageCannotBeRelabeledAsPrivateIdentityMaterial() {
        when(relations.selectCount(any())).thenReturn(1L);
        assertThat(policy.validatePrivateFile(90L, owner, true)).contains("已公开展示");
    }

    @Test
    void explicitIdentityUploadDoesNotDependOnPublicContentTables() {
        file.setAccessPurpose("IDENTITY");
        file.setStorageKey("sensitive/id_photo/photo.png");
        assertThat(policy.validatePrivateFile(90L, owner, true)).isNull();
        verifyNoInteractions(users, relations, dependency("universityMapper"), dependency("noticeMapper"),
                dependency("contestCategoryMapper"), dependency("associationMemberMapper"), dependency("teamMapper"),
                dependency("sysIntroMapper"), dependency("eventMapper"), dependency("trainingMapper"));
        assertThat(policy.canAccess(file, null)).isFalse();
        assertThat(policy.validatePublicFile(90L, owner, true)).contains("不能公开引用");
    }

    @Test
    void explicitIdentityUploadStillChecksOwnershipAndImageType() {
        file.setAccessPurpose("IDENTITY");
        assertThat(policy.validatePrivateFile(90L, actor(8L), true)).contains("自己上传");
        file.setType("pdf");
        assertThat(policy.validatePrivateFile(90L, owner, true)).contains("图片");
    }

    @Test
    void explicitCertificateUploadsDoNotDependOnPublicContentTables() {
        for (boolean template : List.of(false, true)) {
            file.setAccessPurpose(template ? "TEMPLATE" : "CERTIFICATE");
            file.setType(template ? "pdf" : "png");
            assertThat(policy.validateCertificateFile(90L, owner, template)).isNull();
        }
        verifyNoInteractions(relations, dependency("universityMapper"), dependency("noticeMapper"),
                dependency("contestCategoryMapper"), dependency("associationMemberMapper"), dependency("teamMapper"),
                dependency("sysIntroMapper"), dependency("eventMapper"), dependency("trainingMapper"));
        verify(files, never()).updateById(any(FileSystem.class));
    }

    @Test
    void explicitTemplateStillChecksOwnershipFileTypeAndConflictingPrivateUses() {
        file.setAccessPurpose("TEMPLATE");
        file.setType("pdf");
        assertThat(policy.validateCertificateFile(90L, actor(8L), true)).contains("自己上传");
        file.setType("png");
        assertThat(policy.validateCertificateFile(90L, owner, true)).contains("必须为 PDF");
        file.setType("pdf");
        when(users.selectCount(any())).thenReturn(1L);
        assertThat(policy.validateCertificateFile(90L, owner, true)).contains("不能混用");
        verify(files, never()).updateById(any(FileSystem.class));
    }

    @Test
    void legacyCertificateFilesStillRejectPublicReferencesBeforeChangingPurpose() {
        file.setType("pdf");
        when(relations.selectCount(any())).thenReturn(1L);
        assertThat(policy.validateCertificateFile(90L, owner, true)).contains("已公开展示");
        verify(files, never()).updateById(any(FileSystem.class));
        assertThat(file.getAccessPurpose()).isNull();
    }

    @Test
    void nullProjectedContentRowsDoNotBreakReferenceChecksOrHideRealReferences() {
        SysIntroMapper intros = dependency("sysIntroMapper");
        NoticeMapper notices = dependency("noticeMapper");
        EventMapper events = dependency("eventMapper");
        TrainingMapper trainings = dependency("trainingMapper");
        ContestCategoryMapper categories = dependency("contestCategoryMapper");
        AssociationMemberMapper members = dependency("associationMemberMapper");
        TeamMapper teams = dependency("teamMapper");
        when(intros.selectList(any())).thenReturn(java.util.Arrays.asList(null, new SysIntro()));
        when(notices.selectList(any())).thenReturn(java.util.Arrays.asList(null, new Notice()));
        when(events.selectList(any())).thenReturn(java.util.Arrays.asList(null, new Event()));
        when(trainings.selectList(any())).thenReturn(java.util.Arrays.asList(null, new Training()));
        when(categories.selectList(any())).thenReturn(java.util.Arrays.asList(null, new ContestCategory()));
        when(members.selectList(any())).thenReturn(java.util.Arrays.asList(null, new AssociationMember()));
        Team team = new Team();
        when(teams.selectList(any())).thenReturn(java.util.Arrays.asList(null, team));
        assertThat(policy.canAccess(file, null)).isFalse();
        assertThat(policy.validatePrivateFile(90L, owner, true)).isNull();
        file.setAccessPurpose("GENERAL");
        team.setIntroduce("![image](/api/file/image/90)");
        assertThat(policy.canAccess(file, null)).isTrue();
        assertThat(policy.validatePrivateFile(90L, owner, true)).contains("已公开展示");
    }

    @Test
    void managementPermissionsRequireNormalAccountAndExplicitCurrentGrant() {
        Users manager = user(7L, "department", "pending");
        when(users.selectById(7L)).thenReturn(manager);
        AdminPermissions grant = new AdminPermissions();
        grant.setPermissions("[\"manage_contest\"]");
        AdminPermissionsMapper permissions = dependency("adminPermissionsMapper");
        when(permissions.selectOne(any())).thenReturn(grant);
        assertThat(policy.hasPermission(owner, "manage_contest")).isFalse();
        manager.setAccountStatus("normal");
        assertThat(policy.hasPermission(owner, "manage_contest")).isTrue();
        assertThat(policy.hasPermission(owner, "manage_training")).isFalse();
        grant.setPermissions("[]");
        assertThat(policy.hasPermission(owner, "manage_contest")).isFalse();
    }

    @Test
    void maliciousNoticeAttachmentIsRejectedBeforePublishingAnyBusinessRows() {
        NoticeController controller = new NoticeController();
        NoticeMapper notices = mock(NoticeMapper.class);
        ReflectionTestUtils.setField(controller, "noticeMapper", notices);
        ReflectionTestUtils.setField(controller, "fileRelationMapper", relations);
        ReflectionTestUtils.setField(controller, "fileAccessPolicy", policy);
        Users manager = user(8L, "stu_admin", "normal");
        when(users.selectById(8L)).thenReturn(manager);
        AdminPermissions grant = new AdminPermissions();
        grant.setPermissions("[\"manage_notice\"]");
        AdminPermissionsMapper permissions = dependency("adminPermissionsMapper");
        when(permissions.selectOne(any())).thenReturn(grant);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("user", actor(8L));
        NoticeDTO dto = new NoticeDTO();
        dto.setTitle("notice");
        dto.setType("notice");
        dto.setPublisherName("publisher");
        dto.setAttachmentIds(List.of(90L));
        assertThat(controller.publish(dto, request).getMsg()).contains("自己上传");
        verify(notices, never()).insert(any(Notice.class));
        verify(relations, never()).insert(any(FileRelation.class));
    }

    @Test
    void historicalPrivateAttachmentMetadataIsNotReturnedByAPublicNotice() {
        NoticeController controller = new NoticeController();
        NoticeMapper notices = mock(NoticeMapper.class);
        Notice notice = new Notice();
        notice.setInfoId(10L);
        when(notices.selectById(10L)).thenReturn(notice);
        FileRelation relation = new FileRelation();
        relation.setFileId(90L);
        when(relations.selectList(any())).thenReturn(List.of(relation));
        when(relations.selectCount(any())).thenReturn(1L);
        when(files.selectBatchIds(any())).thenReturn(List.of(file));
        file.setAccessPurpose("IDENTITY");
        ReflectionTestUtils.setField(controller, "noticeMapper", notices);
        ReflectionTestUtils.setField(controller, "fileRelationMapper", relations);
        ReflectionTestUtils.setField(controller, "fileMapper", files);
        ReflectionTestUtils.setField(controller, "fileAccessPolicy", policy);
        assertThat(controller.detail(10L, false).getData().getAttachments()).isEmpty();
    }

    @Test
    void privateCertificatePurposeRemainsAfterItsLastBusinessReferenceIsRemoved() {
        assertThat(policy.validateCertificateFile(90L, owner, false)).isNull();
        assertThat(file.getAccessPurpose()).isEqualTo("CERTIFICATE");
        verify(files).updateById(file);
        assertThat(policy.validatePublicFile(90L, owner, true)).contains("不能公开引用");
        assertThat(policy.validatePrivateFile(90L, owner, true)).contains("用途不适用");
    }

    @Test
    void everyAuditReferencePreventsDeletionEvenWhenItHasNoSensitivePurpose() throws Exception {
        AuditRecordsMapper audits = dependency("auditRecordsMapper");
        when(audits.selectCount(any())).thenAnswer(invocation -> {
            QueryWrapper<AuditRecords> query = invocation.getArgument(0);
            return query.getSqlSegment().contains("biz_type") ? 0L : 1L;
        });
        assertThat(policy.deleteOwnedUnreferenced(90L, owner)).contains("仍被业务引用");
        verify(storage, never()).delete(any());
    }

    @Test
    void physicalDeletionWaitsUntilTheBusinessTransactionCommits() throws Exception {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThat(policy.deleteOwnedUnreferenced(90L, owner)).isNull();
            verify(files).deleteById(90L);
            verify(storage, never()).delete(any());
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(storage).delete(file);
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void rollingBackTheBusinessTransactionKeepsPhysicalFilesAvailable() throws Exception {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThat(policy.deleteOwnedUnreferenced(90L, owner)).isNull();
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            verify(storage, never()).delete(any());
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T dependency(String field) { return (T) ReflectionTestUtils.getField(policy, field); }
    private static UserProfileVO actor(Long id) {
        UserProfileVO actor = new UserProfileVO();
        actor.setUserId(id);
        return actor;
    }
    private static Users user(Long id, String role, String status) {
        Users user = new Users();
        user.setUserId(id);
        user.setRole(role);
        user.setAccountStatus(status);
        return user;
    }
}
