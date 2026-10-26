package com.cwnupaa.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.entity.CertificateIssue;
import com.cwnupaa.backend.entity.CertificateIssueMember;
import com.cwnupaa.backend.entity.CertificateLevel;
import com.cwnupaa.backend.entity.CertificateType;
import com.cwnupaa.backend.mapper.CertificateIssueMapper;
import com.cwnupaa.backend.mapper.CertificateIssueMemberMapper;
import com.cwnupaa.backend.mapper.CertificateLevelMapper;
import com.cwnupaa.backend.mapper.CertificateTypeMapper;
import com.cwnupaa.backend.vo.CertificateAuthenticityVO;
import com.cwnupaa.backend.vo.CertificateImportMemberVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class CertificateAuthenticityService {
    @Autowired
    private CertificateIssueMapper issueMapper;
    @Autowired
    private CertificateIssueMemberMapper memberMapper;
    @Autowired
    private CertificateTypeMapper typeMapper;
    @Autowired
    private CertificateLevelMapper levelMapper;

    public CertificateAuthenticityVO verify(String certificateNo) {
        String normalized = normalize(certificateNo);
        CertificateAuthenticityVO result = new CertificateAuthenticityVO();
        if (!StringUtils.hasText(normalized) || normalized.length() > 100) {
            result.setFound(false);
            result.setValid(false);
            result.setMessage("请输入有效的证书编号");
            return result;
        }

        CertificateIssue issue = issueMapper.selectOne(new QueryWrapper<CertificateIssue>()
                .eq("certificate_no", normalized)
                .last("LIMIT 1"));
        if (issue == null) {
            result.setFound(false);
            result.setValid(false);
            result.setMessage("未查询到该编号，请检查编号或联系签发方");
            return result;
        }

        CertificateType type = typeMapper.selectById(issue.getCertificateTypeId());
        List<CertificateIssueMember> members = memberMapper.selectList(new QueryWrapper<CertificateIssueMember>()
                .eq("issue_id", issue.getIssueId())
                .orderByAsc("sort_order", "issue_member_id"));

        result.setFound(true);
        result.setValid("active".equals(issue.getStatus()));
        result.setMessage(result.isValid() ? "该证书签发记录有效" : "该证书已被撤销");
        result.setCertificateNo(issue.getCertificateNo());
        result.setCertificateName(type == null ? "电子证书" : type.getTypeName());
        result.setCertificateKind(Integer.valueOf(1).equals(issue.getCertificateKind()) ? "团队证书" : "个人证书");
        result.setLevelScope(resolveLevelScope(issue, type));
        result.setTeamName(issue.getTeamName());
        result.setAwardName(issue.getAwardName());
        result.setIssueDate(type != null && type.getTime() != null ? type.getTime() : issue.getIssueDate());
        result.setMembers(members.stream()
                .sorted(Comparator.comparing(member -> member.getSortOrder() == null ? Integer.MAX_VALUE : member.getSortOrder()))
                .map(member -> new CertificateImportMemberVO(member.getMemberName(), maskStudentId(member.getStudentId()), member.getSchoolName()))
                .collect(Collectors.toList()));
        return result;
    }

    private String maskStudentId(String studentId) {
        if (!StringUtils.hasText(studentId)) return null;
        if (studentId.length() <= 4) return "****";
        return studentId.substring(0, 2) + "****" + studentId.substring(studentId.length() - 2);
    }

    private String normalize(String number) {
        return number == null ? null : number.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveLevelScope(CertificateIssue issue, CertificateType type) {
        if (type == null) return issue.getLevelScope();
        if (StringUtils.hasText(type.getLevelScope())) return type.getLevelScope();
        if (StringUtils.hasText(issue.getLevelScope())) return issue.getLevelScope();
        if (type.getTypeId() == null) return null;
        CertificateLevel level = levelMapper.selectById(type.getTypeId());
        return level == null ? null : level.getLevelType();
    }
}
