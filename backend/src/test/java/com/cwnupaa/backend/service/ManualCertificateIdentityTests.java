package com.cwnupaa.backend.service;

import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ManualCertificateIdentityTests {
    private Users verified(Long id) { Users user = new Users();user.setUserId(id);user.setAccountStatus("normal");user.setNumberId("001");user.setRealName("张三");return user; }
    @Test void historicalWrongAndSchoollessMemberBindingsDoNotReceiveRating() {
        RatingService rating = new RatingService();
        CertificateIdentityService identities = spy(new CertificateIdentityService());
        UserMapper users = mock(UserMapper.class); ReflectionTestUtils.setField(identities,"userMapper",users);
        when(users.selectById(1L)).thenReturn(verified(1L)); when(users.selectById(2L)).thenReturn(verified(2L));
        doReturn("学校A").when(identities).schoolName(any());
        ReflectionTestUtils.setField(rating,"certificateIdentityService",identities);
        CertificateMemberMapper members = mock(CertificateMemberMapper.class); ReflectionTestUtils.setField(rating,"certificateMemberMapper",members);
        CertificateVerify certificate = new CertificateVerify();certificate.setCertificateVerifyId(8L);certificate.setUserId(1L);certificate.setLevelId(3L);
        CertificateMember member = new CertificateMember();member.setCertificateVerifyId(8L);member.setUserId(2L);member.setStudentId("001");member.setName("张三");
        when(members.selectList(any())).thenReturn(List.of(member));
        for (String school : new String[]{null,"学校B","学校A"}) {
            member.setSchoolName(school);
            RatingComposition applicant = new RatingComposition();applicant.setRatingCertificate(0);
            RatingComposition recipient = new RatingComposition();recipient.setRatingCertificate(0);
            ReflectionTestUtils.invokeMethod(rating,"calculateCertificateScoreInTerm",1L,Map.of(1L,applicant,2L,recipient),Map.of(),Map.of(1L,List.of(certificate)),Map.of(3L,100),Map.of(),List.of(),Map.of(),Map.of());
            assertThat(applicant.getRatingCertificate()).isEqualTo(100);
            assertThat(recipient.getRatingCertificate()).isEqualTo("学校A".equals(school) ? 100 : 0);
        }
        member.setName("李四");
        assertThat(identities.ownsMember(verified(2L),member)).isFalse();
    }
    @Test void manualMemberConfirmationRequiresApprovedOriginalMaterialAndAuditsBinding() {
        CertificateGrantService service = new CertificateGrantService();
        CertificateIdentityService identities = mock(CertificateIdentityService.class);
        Users user = verified(2L);when(identities.verifiedUser(2L)).thenReturn(user);when(identities.matches(user,"学校A","001","张三")).thenReturn(true);
        CertificateMemberMapper members = mock(CertificateMemberMapper.class);CertificateVerifyMapper certificates = mock(CertificateVerifyMapper.class);SecurityAuditService audit = mock(SecurityAuditService.class);
        CertificateMember member = new CertificateMember();member.setMemberId(4L);member.setCertificateVerifyId(8L);member.setName("张三");member.setStudentId("001");when(members.selectOne(any())).thenReturn(member);
        CertificateVerify certificate = new CertificateVerify();certificate.setStatus("pending");when(certificates.selectOne(any())).thenReturn(certificate);
        ReflectionTestUtils.setField(service,"certificateIdentityService",identities);ReflectionTestUtils.setField(service,"certificateMemberMapper",members);ReflectionTestUtils.setField(service,"certificateVerifyMapper",certificates);ReflectionTestUtils.setField(service,"securityAuditService",audit);
        assertThat(service.confirmMemberIdentity(4L,2L,"学校A","001","张三",9L).getCode()).isNotEqualTo(200);verify(members,never()).updateById(any(CertificateMember.class));
        certificate.setStatus("approved");
        assertThat(service.confirmMemberIdentity(4L,2L,"学校A","001","张三",9L).getCode()).isEqualTo(200);assertThat(member.getUserId()).isEqualTo(2L);assertThat(member.getSchoolName()).isEqualTo("学校A");
        verify(audit).record(9L,2L,"certificate_member_identity_confirm:4",List.of("school_name","user_id"));
    }
}
