package com.cwnupaa.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cwnupaa.backend.entity.CertificateType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CertificateTypeMapper extends BaseMapper<CertificateType> {
    // Serialize issuance for a template through commit, including across backend instances.
    @Select("SELECT * FROM certificate_type WHERE certificate_type_id = #{typeId} FOR UPDATE")
    CertificateType selectForIssueUpdate(@Param("typeId") Long typeId);
}
