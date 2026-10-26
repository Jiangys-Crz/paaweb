package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.entity.FileSystem;
import com.cwnupaa.backend.mapper.AdminPermissionsMapper;
import com.cwnupaa.backend.mapper.AssociationMemberMapper;
import com.cwnupaa.backend.mapper.CertificateTypeMapper;
import com.cwnupaa.backend.mapper.CertificateVerifyMapper;
import com.cwnupaa.backend.mapper.ContestCategoryMapper;
import com.cwnupaa.backend.mapper.FileRelationMapper;
import com.cwnupaa.backend.mapper.FileSystemMapper;
import com.cwnupaa.backend.mapper.NoticeMapper;
import com.cwnupaa.backend.mapper.SysIntroMapper;
import com.cwnupaa.backend.mapper.UniversityMapper;
import com.cwnupaa.backend.mapper.UserMapper;
import com.cwnupaa.backend.service.FileStorageService;
import com.cwnupaa.backend.service.FileAccessPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 文件访问策略回归：出现在公开页面上的展示字段必须放行匿名访问，
 * 而实名材料、证件照、证书材料等私有文件必须继续拒绝。
 */
class FileControllerAccessTests {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final UniversityMapper universityMapper = mock(UniversityMapper.class);
    private final FileRelationMapper fileRelationMapper = mock(FileRelationMapper.class);
    private final SysIntroMapper sysIntroMapper = mock(SysIntroMapper.class);
    private final NoticeMapper noticeMapper = mock(NoticeMapper.class);
    private final ContestCategoryMapper contestCategoryMapper = mock(ContestCategoryMapper.class);
    private final AssociationMemberMapper associationMemberMapper = mock(AssociationMemberMapper.class);
    private final CertificateTypeMapper certificateTypeMapper = mock(CertificateTypeMapper.class);
    private final CertificateVerifyMapper certificateVerifyMapper = mock(CertificateVerifyMapper.class);

    private FileController controller;

    @BeforeEach
    void setUp() {
        controller = new FileController();
        FileAccessPolicy policy = new FileAccessPolicy();
        for (Field field : FileAccessPolicy.class.getDeclaredFields()) {
            if (field.isAnnotationPresent(Autowired.class)) ReflectionTestUtils.setField(policy, field.getName(), mock(field.getType()));
        }
        ReflectionTestUtils.setField(policy, "userMapper", userMapper);
        ReflectionTestUtils.setField(policy, "universityMapper", universityMapper);
        ReflectionTestUtils.setField(policy, "fileRelationMapper", fileRelationMapper);
        ReflectionTestUtils.setField(policy, "sysIntroMapper", sysIntroMapper);
        ReflectionTestUtils.setField(policy, "noticeMapper", noticeMapper);
        ReflectionTestUtils.setField(policy, "contestCategoryMapper", contestCategoryMapper);
        ReflectionTestUtils.setField(policy, "associationMemberMapper", associationMemberMapper);
        ReflectionTestUtils.setField(policy, "certificateTypeMapper", certificateTypeMapper);
        ReflectionTestUtils.setField(policy, "certificateVerifyMapper", certificateVerifyMapper);
        ReflectionTestUtils.setField(controller, "fileAccessPolicy", policy);
    }

    @Test
    void anonymousVisitorCanReadContestCategoryLogo() {
        when(contestCategoryMapper.selectCount(any())).thenReturn(1L);

        assertThat(controller.canAccess(file(46L, 1L), null)).isTrue();
    }

    @Test
    void anonymousVisitorCanReadNoticeCover() {
        when(noticeMapper.selectCount(any())).thenReturn(1L);

        assertThat(controller.canAccess(file(900L, 1L), null)).isTrue();
    }

    @Test
    void anonymousVisitorCanReadAssociationMemberAvatar() {
        when(associationMemberMapper.selectCount(any())).thenReturn(1L);

        assertThat(controller.canAccess(file(901L, 1L), null)).isTrue();
    }

    @Test
    void anonymousVisitorCanReadBusinessAttachment() {
        when(fileRelationMapper.selectCount(any())).thenReturn(1L);

        assertThat(controller.canAccess(file(399L, 1L), null)).isTrue();
    }

    @Test
    void anonymousVisitorCannotReadUnreferencedPrivateFile() {
        assertThat(controller.canAccess(file(500L, 7L), null)).isFalse();
    }

    @Test
    void anonymousVisitorCannotReadIdentityMaterial() {
        when(userMapper.selectCount(any())).thenAnswer(invocation ->
                ((com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<?>) invocation.getArgument(0)).getSqlSegment().contains("id_photo_file_id") ? 1L : 0L);

        assertThat(controller.canAccess(file(388L, 7L), null)).isFalse();
    }

    @Test
    void identityMaterialStaysPrivateWhenAlsoReferencedByPublicNotice() {
        when(userMapper.selectCount(any())).thenAnswer(invocation ->
                ((com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<?>) invocation.getArgument(0)).getSqlSegment().contains("id_photo_file_id") ? 1L : 0L);
        when(noticeMapper.selectCount(any())).thenReturn(1L);
        when(fileRelationMapper.selectCount(any())).thenReturn(1L);
        assertThat(controller.canAccess(file(388L, 7L), null)).isFalse();
    }

    private FileSystem file(Long fileId, Long uploaderId) {
        FileSystem file = new FileSystem();
        file.setFileId(fileId);
        file.setUploaderId(uploaderId);
        file.setName(fileId + ".pdf");
        file.setUrl("/uploads/" + fileId + ".pdf");
        return file;
    }
}
