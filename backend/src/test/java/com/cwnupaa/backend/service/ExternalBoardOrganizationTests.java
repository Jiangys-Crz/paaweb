package com.cwnupaa.backend.service;

import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.vo.RankItemVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class ExternalBoardOrganizationTests {
    @Test
    void displayModeChoosesCorrectNameAndFallsBackToUniversityLogo() {
        ExternalBoardService service = new ExternalBoardService();
        UserMapper users = mock(UserMapper.class);
        MajorMapper majors = mock(MajorMapper.class);
        SchoolMapper schools = mock(SchoolMapper.class);
        UniversityMapper universities = mock(UniversityMapper.class);
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(service, "majorMapper", majors);
        ReflectionTestUtils.setField(service, "schoolMapper", schools);
        ReflectionTestUtils.setField(service, "universityMapper", universities);
        Users user = new Users(); user.setUserId(1L); user.setMajorId(2L);
        Major major = new Major(); major.setMajorId(2L); major.setSchoolId(3L);
        School school = new School(); school.setSchoolId(3L); school.setUniversityId(4L); school.setSchoolName("学院");
        University university = new University(); university.setUniversityId(4L); university.setUniversityName("大学"); university.setLogoFileId(5L);
        when(users.selectBatchIds(anyCollection())).thenReturn(List.of(user));
        when(majors.selectBatchIds(anyCollection())).thenReturn(List.of(major));
        when(schools.selectBatchIds(anyCollection())).thenReturn(List.of(school));
        when(universities.selectBatchIds(anyCollection())).thenReturn(List.of(university));
        RankItemVO row = new RankItemVO(); row.setMemberIds(List.of(1L));
        ReflectionTestUtils.invokeMethod(service, "enrichOrganizations", List.of(row), "school");
        assertThat(row.getOrganization()).isEqualTo("学院");
        assertThat(row.getOrganizationLogo()).isEqualTo("/api/file/image/5");
        school.setLogoFileId(6L);
        ReflectionTestUtils.invokeMethod(service, "enrichOrganizations", List.of(row), "school");
        assertThat(row.getOrganizationLogo()).isEqualTo("/api/file/image/6");
        ReflectionTestUtils.invokeMethod(service, "enrichOrganizations", List.of(row), "university");
        assertThat(row.getOrganization()).isEqualTo("大学");
        assertThat(row.getOrganizationLogo()).isEqualTo("/api/file/image/5");
    }
}
