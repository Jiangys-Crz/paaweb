package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.dto.CertificateVerifyDTO;
import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.service.CertificateIdentityService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Date;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class CertificateMemberPrivacyTests {
    @Test void arbitraryUserIdCannotHarvestVerifiedIdentityThroughSubmission() {
        CertificateController controller = new CertificateController();
        CertificateIdentityService identities = mock(CertificateIdentityService.class);
        Users applicant = new Users();applicant.setUserId(1L);applicant.setAccountStatus("normal");
        when(identities.verifiedUser(1L)).thenReturn(applicant);
        TeamMapper teams = mock(TeamMapper.class);when(teams.selectList(any())).thenReturn(List.of());
        CertificateVerifyMapper certificates = mock(CertificateVerifyMapper.class);
        ReflectionTestUtils.setField(controller,"certificateIdentityService",identities);ReflectionTestUtils.setField(controller,"teamMapper",teams);ReflectionTestUtils.setField(controller,"certificateVerifyMapper",certificates);
        CertificateVerifyDTO input = new CertificateVerifyDTO();input.setFileId(1L);input.setLevelId(1L);input.setCategoryId(1L);input.setTermId(1L);input.setEventName("活动");input.setTime(new Date());input.setIsTeam(1);
        CertificateVerifyDTO.MemberDTO member = new CertificateVerifyDTO.MemberDTO();member.setUserId(99L);input.setMembers(List.of(member));
        assertThat(controller.submit(input,1L).getCode()).isNotEqualTo(200);
        verify(identities,never()).verifiedUser(99L);verifyNoInteractions(certificates);
    }
}
