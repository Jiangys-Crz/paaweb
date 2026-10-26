package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.service.AdmissionTicketPdfService;
import com.cwnupaa.backend.vo.MySignupInfoVO;
import com.cwnupaa.backend.vo.UserProfileVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class EventSignupMemberPrivacyTests {
    private EventController controller;
    private UserMapper users;
    private List<Users> teamUsers;
    private Event event;
    private SignupTeam signup;

    @BeforeEach
    void setUp() {
        controller = new EventController();
        for (Field field : EventController.class.getDeclaredFields()) {
            if (field.isAnnotationPresent(Autowired.class)) ReflectionTestUtils.setField(controller, field.getName(), mock(field.getType()));
        }
        event = new Event();
        event.setEventId(5L); event.setIsTeam(1);
        EventMapper events = dependency("eventMapper");
        when(events.selectById(5L)).thenReturn(event);
        signup = new SignupTeam();
        signup.setSignupId(10L); signup.setStatus("pending"); signup.setType("official");
        // A staff importer/creator is not the captain of the signed-up team.
        signup.setCreatedByUserId(3L);
        SignupTeamMapper signups = dependency("signupTeamMapper");
        when(signups.selectLatestByMember(anyLong(), anyLong())).thenReturn(signup);
        SignupTeamDetailMapper details = dependency("signupTeamDetailMapper");
        when(details.selectList(any())).thenReturn(List.of(detail(1L, 0), detail(2L, 1), detail(3L, 2)));
        teamUsers = List.of(user(1L, "队长", "张三", "001"), user(2L, "本人", "李四", "002"), user(3L, "队友", "王五", "003"));
        users = dependency("userMapper");
        when(users.selectBatchIds(any())).thenReturn(teamUsers);
        Major major = new Major(); major.setSchoolId(20L); major.setMajorName("专业");
        MajorMapper majors = dependency("majorMapper"); when(majors.selectById(10L)).thenReturn(major);
        School school = new School(); school.setSchoolName("学院"); school.setUniversityId(30L);
        SchoolMapper schools = dependency("schoolMapper"); when(schools.selectById(20L)).thenReturn(school);
        University university = new University(); university.setUniversityName("大学");
        UniversityMapper universities = dependency("universityMapper"); when(universities.selectById(30L)).thenReturn(university);
    }

    @Test
    void memberGetsOwnIdentityAndOnlyPublicDetailsOfOtherMembers() throws Exception {
        List<MySignupInfoVO.SimpleMemberInfo> members = controller.getMySignupDetail(5L, request(2L)).getData().getMembers();
        assertThat(members).extracting(MySignupInfoVO.SimpleMemberInfo::getRealName).containsExactly("张*", "李四", "王*");
        assertThat(members).extracting(MySignupInfoVO.SimpleMemberInfo::getNumberId).containsExactly(null, "002", null);
        assertThat(members).extracting(MySignupInfoVO.SimpleMemberInfo::getUniversityName).containsExactly(null, "大学", null);
        assertThat(members).extracting(MySignupInfoVO.SimpleMemberInfo::getIdPhotoUrl).containsExactly(null, "/api/user/id-photo/2", null);
        assertThat(members).extracting(MySignupInfoVO.SimpleMemberInfo::getUserName).containsExactly("队长", "本人", "队友");
        assertThat(members.get(0).getAvatarUrl()).isEqualTo("/api/file/image/41");
        String json = new ObjectMapper().writeValueAsString(members.get(2));
        assertThat(json).doesNotContain("003", "大学", "学院", "专业", "2026", "/api/user/id-photo/3");
    }

    @Test
    void snapshotCaptainGetsNecessaryIdentityWhileCreatorDoesNotGetCaptainPrivileges() {
        List<MySignupInfoVO.SimpleMemberInfo> captainMembers = controller.getMySignupDetail(5L, request(1L)).getData().getMembers();
        assertThat(captainMembers).extracting(MySignupInfoVO.SimpleMemberInfo::getNumberId).containsExactly("001", "002", "003");
        assertThat(captainMembers).extracting(MySignupInfoVO.SimpleMemberInfo::getSchoolName).containsExactly("学院", "学院", "学院");
        List<MySignupInfoVO.SimpleMemberInfo> creatorMembers = controller.getMySignupDetail(5L, request(3L)).getData().getMembers();
        assertThat(creatorMembers).extracting(MySignupInfoVO.SimpleMemberInfo::getNumberId).containsExactly(null, null, "003");
    }

    @Test
    void missingCaptainAccountDoesNotPromoteTheNextLoadedMember() {
        when(users.selectBatchIds(any())).thenReturn(teamUsers.subList(1, 3));
        List<MySignupInfoVO.SimpleMemberInfo> members = controller.getMySignupDetail(5L, request(2L)).getData().getMembers();
        assertThat(members).extracting(MySignupInfoVO.SimpleMemberInfo::isCaptain).containsExactly(false, false);
        assertThat(members).extracting(MySignupInfoVO.SimpleMemberInfo::getNumberId).containsExactly("002", null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ordinaryMemberTicketOnlyReceivesTheirOwnProfile() throws Exception {
        AdmissionTicketPdfService tickets = enableTickets(2L);
        assertThat(controller.downloadAdmissionTicket(5L, request(2L)).getStatusCode().value()).isEqualTo(200);
        ArgumentCaptor<List<Users>> profiles = ArgumentCaptor.forClass(List.class);
        verify(tickets).renderTeam(any(), any(), any(), any(), profiles.capture());
        assertThat(profiles.getValue()).extracting(Users::getUserId).containsExactly(2L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void captainTicketCanStillRenderTheTeamNames() throws Exception {
        AdmissionTicketPdfService tickets = enableTickets(1L);
        assertThat(controller.downloadAdmissionTicket(5L, request(1L)).getStatusCode().value()).isEqualTo(200);
        ArgumentCaptor<List<Users>> profiles = ArgumentCaptor.forClass(List.class);
        verify(tickets).renderTeam(any(), any(), any(), any(), profiles.capture());
        assertThat(profiles.getValue()).extracting(Users::getUserId).containsExactly(1L, 2L, 3L);
    }

    @Test
    void aRemovedMemberCannotDownloadAFormerSharedTeamTicket() throws Exception {
        AdmissionTicketPdfService tickets = enableTickets(2L);
        SignupTeamDetailMapper details = dependency("signupTeamDetailMapper");
        when(details.selectList(any())).thenReturn(List.of(detail(1L, 0), detail(3L, 2)));
        assertThat(controller.downloadAdmissionTicket(5L, request(2L)).getStatusCode().value()).isEqualTo(400);
        verify(tickets, never()).renderTeam(any(), any(), any(), any(), any());
    }

    private AdmissionTicketPdfService enableTickets(Long actorId) throws Exception {
        event.setName("比赛"); event.setAdmissionTicketOpen(true); signup.setStatus("approved");
        when(users.selectById(actorId)).thenReturn(teamUsers.stream().filter(u -> u.getUserId().equals(actorId)).findFirst().orElseThrow());
        AdmissionTicketPdfService tickets = dependency("admissionTicketPdfService");
        when(tickets.hasCredential(any(SignupTeam.class), any(SignupTeamDetail.class))).thenReturn(true);
        when(tickets.renderTeam(any(), any(), any(), any(), any())).thenReturn(new byte[]{1});
        return tickets;
    }

    private static SignupTeamDetail detail(Long id, int order) {
        SignupTeamDetail detail = new SignupTeamDetail(); detail.setUserId(id); detail.setMemberOrder(order); return detail;
    }
    private static Users user(Long id, String nickname, String name, String number) {
        Users user = new Users(); user.setUserId(id); user.setUserName(nickname); user.setRealName(name); user.setNumberId(number);
        user.setMajorId(10L); user.setGender(1); user.setGradeYear("2026"); user.setIdPhotoFileId(50L + id); user.setAvatarFileId(40L + id);
        return user;
    }
    private static MockHttpServletRequest request(Long id) {
        MockHttpServletRequest request = new MockHttpServletRequest(); UserProfileVO user = new UserProfileVO(); user.setUserId(id);
        request.setAttribute("user", user); return request;
    }
    @SuppressWarnings("unchecked") private <T> T dependency(String name) { return (T) ReflectionTestUtils.getField(controller, name); }
}
