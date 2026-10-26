package com.cwnupaa.backend.service;

import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.utils.StudentNumberValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Single identity rule for certificate binding, ownership, public honors and rating. */
@Service
public class CertificateIdentityService {
    @Autowired private UserMapper userMapper;
    @Autowired private MajorMapper majorMapper;
    @Autowired private SchoolMapper schoolMapper;
    @Autowired private UniversityMapper universityMapper;

    public Users verifiedUser(Long userId) {
        Users user = userId == null ? null : userMapper.selectById(userId);
        return user != null && "normal".equals(user.getAccountStatus()) ? user : null;
    }

    public String schoolName(Users user) {
        return user == null || user.getMajorId() == null ? null
                : StudentNumberValidator.resolveUniversityName(majorMapper, schoolMapper, universityMapper, user.getMajorId());
    }

    public boolean matches(Users user, String school, String number, String name) {
        return matches(user, schoolName(user), school, number, name);
    }

    public static boolean matches(Users user, String userSchool, String school, String number, String name) {
        return user != null && "normal".equals(user.getAccountStatus())
                && same(userSchool, school) && same(user.getNumberId(), number) && same(user.getRealName(), name);
    }

    public boolean owns(Users user, CertificateGrant grant) {
        return grant != null && user != null && user.getUserId().equals(grant.getUserId())
                && matches(user, grant.getSchoolName(), grant.getUserAccount(), grant.getRealName());
    }

    public boolean ownsMember(Users user, CertificateMember member) {
        return user != null && member != null && user.getUserId().equals(member.getUserId())
                && matches(user, member.getSchoolName(), member.getStudentId(), member.getName());
    }

    public static boolean same(String first, String second) {
        return StringUtils.hasText(first) && StringUtils.hasText(second) && first.trim().equals(second.trim());
    }
}
