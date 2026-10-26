package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.service.AdmissionTicketPdfService;
import com.cwnupaa.backend.vo.UserProfileVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EventIdPhotoTests {
    private EventController controller;
    private Event event;
    private Users user;
    private SignupPersonal personal;
    private SignupTeam team;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        controller = new EventController();
        for (Field field : EventController.class.getDeclaredFields()) {
            if (field.isAnnotationPresent(Autowired.class)) {
                ReflectionTestUtils.setField(controller, field.getName(), mock(field.getType()));
            }
        }
        event = new Event();
        event.setEventId(5L);
        event.setName("导入报名比赛");
        event.setIsTeam(0);
        event.setSignupEnd(LocalDateTime.now().minusDays(1));
        event.setEventEnd(LocalDateTime.now().plusDays(1));
        event.setAdmissionTicketOpen(true);
        EventMapper events = dependency("eventMapper");
        when(events.selectById(5L)).thenReturn(event);

        user = new Users();
        user.setUserId(7L);
        user.setRealName("示例乙");
        user.setMajorId(1L);
        user.setGender(1);
        UserMapper users = dependency("userMapper");
        when(users.selectById(7L)).thenReturn(user);
        when(users.selectBatchIds(any())).thenReturn(List.of(user));

        personal = new SignupPersonal();
        personal.setStatus("approved");
        personal.setSignupSource("admin_import");
        personal.setContestAccount("contest007");
        personal.setContestPassword("password007");
        personal.setSeatNumber("A-007");
        SignupPersonalMapper personalSignups = dependency("signupPersonalMapper");
        when(personalSignups.selectOne(any())).thenReturn(personal);

        team = new SignupTeam();
        team.setSignupId(10L);
        team.setStatus("approved");
        team.setType("official");
        SignupTeamMapper teamSignups = dependency("signupTeamMapper");
        when(teamSignups.selectLatestByMember(5L, 7L)).thenReturn(team);
        SignupTeamDetail detail = new SignupTeamDetail();
        detail.setUserId(7L);
        detail.setMemberOrder(0);
        SignupTeamDetailMapper details = dependency("signupTeamDetailMapper");
        when(details.selectList(any())).thenReturn(List.of(detail));

        request = new MockHttpServletRequest();
        UserProfileVO snapshot = new UserProfileVO();
        snapshot.setUserId(7L);
        request.setAttribute("user", snapshot);
    }

    @Test
    void importedPersonalSignupWithoutPhotoRemainsVisibleAfterSignupClosesAndTicketsOpen() {
        var result = controller.getMySignupDetail(5L, request);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getStatus()).isEqualTo("approved");
        assertThat(result.getData().getContestAccount()).isEqualTo("contest007");
        assertThat(result.getData().getContestPassword()).isEqualTo("password007");
        assertThat(result.getData().getSeatNumber()).isEqualTo("A-007");
        assertThat(result.getData().getMembers()).hasSize(1);
        assertThat(result.getData().getMembers().get(0).getUserId()).isEqualTo(7L);
        assertThat(result.getData().getMembers().get(0).getIdPhotoUrl()).isNull();
    }

    @Test
    void pendingPersonalSignupWithoutPhotoRemainsVisibleWithoutExposingCredentials() {
        personal.setStatus("pending");
        var result = controller.getMySignupDetail(5L, request);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getStatus()).isEqualTo("pending");
        assertThat(result.getData().getContestPassword()).isNull();
    }

    @Test
    void importedTeamMemberWithoutPhotoCanReadOwnSignup() {
        event.setIsTeam(1);
        var result = controller.getMySignupDetail(5L, request);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getStatus()).isEqualTo("approved");
        assertThat(result.getData().getMembers().get(0).getUserId()).isEqualTo(7L);
        assertThat(result.getData().getMembers().get(0).getIdPhotoUrl()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void eachTicketDownloadReadsTheLatestPhotoAfterUploadAndReplacement(int isTeam) throws Exception {
        event.setIsTeam(isTeam);
        AdmissionTicketPdfService tickets = dependency("admissionTicketPdfService");
        List<Long> renderedPhotos = new ArrayList<>();
        when(tickets.hasCredential(any(SignupPersonal.class))).thenReturn(true);
        when(tickets.hasCredential(any(SignupTeam.class), any(SignupTeamDetail.class))).thenReturn(true);
        when(tickets.renderPersonal(any(), any(), any())).thenAnswer(call -> {
            renderedPhotos.add(((Users) call.getArgument(2)).getIdPhotoFileId());
            return new byte[]{1};
        });
        when(tickets.renderTeam(any(), any(), any(), any(), any())).thenAnswer(call -> {
            renderedPhotos.add(((Users) call.getArgument(3)).getIdPhotoFileId());
            return new byte[]{1};
        });

        assertThat(controller.downloadAdmissionTicket(5L, request).getStatusCode().value()).isEqualTo(200);
        user.setIdPhotoFileId(9L);
        assertThat(controller.downloadAdmissionTicket(5L, request).getStatusCode().value()).isEqualTo(200);
        user.setIdPhotoFileId(11L);
        assertThat(controller.downloadAdmissionTicket(5L, request).getStatusCode().value()).isEqualTo(200);
        assertThat(renderedPhotos).containsExactly(null, 9L, 11L);
        assertThat(controller.getMySignupDetail(5L, request).getData().getMembers().get(0).getIdPhotoUrl())
                .isEqualTo("/api/user/id-photo/7");
    }

    @SuppressWarnings("unchecked")
    private <T> T dependency(String name) {
        return (T) ReflectionTestUtils.getField(controller, name);
    }
}
