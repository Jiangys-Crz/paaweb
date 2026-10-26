package com.cwnupaa.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cwnupaa.backend.entity.CertificateVerify;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CertificateVerifyMapper extends BaseMapper<CertificateVerify> {

    String IDENTITY_ACCESS = "(cv.user_id = #{userId} AND EXISTS (SELECT 1 FROM users owner WHERE owner.user_id = #{userId} AND owner.account_status = 'normal')) " +
            "OR EXISTS (SELECT 1 FROM certificate_member cm JOIN users u ON u.user_id = cm.user_id " +
            "JOIN major m ON m.major_id = u.major_id JOIN school s ON s.school_id = m.school_id " +
            "JOIN university un ON un.university_id = s.university_id " +
            "WHERE cm.certificate_verify_id = cv.certificate_verify_id AND cm.user_id = #{userId} AND u.account_status = 'normal' " +
            "AND NULLIF(TRIM(cm.school_name), '') IS NOT NULL AND TRIM(cm.school_name) = TRIM(un.university_name) " +
            "AND NULLIF(TRIM(cm.name), '') IS NOT NULL AND TRIM(cm.name) = TRIM(u.real_name) " +
            "AND NULLIF(TRIM(cm.student_id), '') IS NOT NULL AND TRIM(cm.student_id) = TRIM(u.number_id))";

    @Select("SELECT cv.* FROM certificate_verify cv WHERE (" + IDENTITY_ACCESS + ") ORDER BY cv.create_time DESC")
    IPage<CertificateVerify> selectMyCertificates(Page<?> page, @Param("userId") Long userId);

    @Select("SELECT cv.* FROM certificate_verify cv WHERE (" + IDENTITY_ACCESS + ") AND cv.status = 'approved' ORDER BY cv.create_time DESC")
    List<CertificateVerify> selectMyHonorWall(@Param("userId") Long userId);
}
