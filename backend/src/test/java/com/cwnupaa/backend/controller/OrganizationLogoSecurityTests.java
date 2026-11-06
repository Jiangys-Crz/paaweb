package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.entity.School;
import com.cwnupaa.backend.entity.University;
import com.cwnupaa.backend.mapper.SchoolMapper;
import com.cwnupaa.backend.mapper.UniversityMapper;
import com.cwnupaa.backend.vo.UserProfileVO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OrganizationLogoSecurityTests {
    @Test
    void oldOrganizationEndpointsDiscardInjectedLogoIds() {
        OrganizationController controller = new OrganizationController();
        SchoolMapper schools = mock(SchoolMapper.class);
        UniversityMapper universities = mock(UniversityMapper.class);
        ReflectionTestUtils.setField(controller, "schoolMapper", schools);
        ReflectionTestUtils.setField(controller, "universityMapper", universities);
        School school = new School(); school.setSchoolId(2L); school.setUniversityId(1L); school.setSchoolName("学院"); school.setLogoFileId(99L);
        University university = new University(); university.setUniversityId(1L); university.setUniversityName("大学"); university.setLogoFileId(99L);
        assertThat(controller.saveSchool(school).getCode()).isEqualTo(200);
        assertThat(controller.saveUniversity(university).getCode()).isEqualTo(200);
        verify(schools).updateById(argThat((School s) -> s.getLogoFileId() == null));
        verify(universities).updateById(argThat((University u) -> u.getLogoFileId() == null));
    }

    @Test
    void logoEndpointRejectsNonSuperAdmins() {
        OrganizationController controller = new OrganizationController();
        MockHttpServletRequest request = new MockHttpServletRequest();
        UserProfileVO user = new UserProfileVO();
        user.setRole("stu_admin"); user.setAccountStatus("normal");
        request.setAttribute("user", user);
        assertThat(controller.organizationInfo(request).getCode()).isEqualTo(403);
        assertThat(controller.updateOrganizationLogo("school", 1L, Map.of("logoFileId", 3L), request).getCode()).isEqualTo(403);
    }
}
