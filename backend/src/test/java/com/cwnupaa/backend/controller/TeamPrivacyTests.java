package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.vo.UserProfileVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class TeamPrivacyTests {
    private TeamController controller;
    private TeamMemberMapper members;
    private UserMapper users;
    private TeamMember relation;
    private Team team;
    @BeforeEach void setup() {
        controller = new TeamController(); members = mock(TeamMemberMapper.class); users = mock(UserMapper.class);
        TeamMapper teams = mock(TeamMapper.class);
        ReflectionTestUtils.setField(controller,"teamMemberMapper",members);
        ReflectionTestUtils.setField(controller,"userMapper",users); ReflectionTestUtils.setField(controller,"teamMapper",teams);
        relation = new TeamMember(); relation.setTeamId(1L); relation.setMemberId(2L); relation.setStatus("1");
        team = new Team(); team.setTeamId(1L); team.setLeaderId(1L); team.setTeamName("队伍");
        when(teams.selectById(1L)).thenReturn(team);
        when(users.selectById(1L)).thenReturn(user(1L,"队长","张三","001"));
        when(users.selectById(2L)).thenReturn(user(2L,"本人","李四","002"));
        when(users.selectById(3L)).thenReturn(user(3L,"队友","王五","003"));
    }
    private Users user(Long id,String nickname,String name,String number) {
        Users u = new Users(); u.setUserId(id); u.setUserName(nickname); u.setRealName(name); u.setNumberId(number);
        u.setEmail("private@example.com"); u.setPassword("secret"); u.setPhone("13900000000"); return u;
    }
    private MockHttpServletRequest request(Long id) {
        MockHttpServletRequest req = new MockHttpServletRequest(); UserProfileVO u = new UserProfileVO();u.setUserId(id);req.setAttribute("user",u);return req;
    }
    private TeamMember member(Long id) { TeamMember m = new TeamMember();m.setMemberId(id);m.setStatus("1");return m; }
    @Test void pendingInvitationReturnsSummaryWithoutLoadingTeamMembers() {
        relation.setStatus("0");when(members.selectList(any())).thenReturn(List.of(relation));
        var result = controller.myTeams(request(2L)).getData().get(0);
        assertThat(result.getMembers()).isEmpty();assertThat(result.getLeaderName()).isEqualTo("队长");
        verify(members,times(1)).selectList(any()); verify(users,never()).selectById(3L);
    }
    @Test void memberOnlySeesOwnNumberAndMaskedOtherNames() throws Exception {
        when(members.selectList(any())).thenReturn(List.of(relation),List.of(member(1L),member(2L),member(3L)));
        var result = controller.myTeams(request(2L)).getData().get(0);
        assertThat(result.getMembers()).extracting("numberId").containsExactly(null,"002",null);
        assertThat(result.getMembers()).extracting("realName").containsExactly("张*","李四","王*");
        String json = new ObjectMapper().writeValueAsString(result);
        assertThat(json).doesNotContain("email","password","phone","private@example.com","secret","003");
    }
    @Test void leaderGetsOnlyNecessaryIdentityFieldsForAcceptedMembers() throws Exception {
        when(members.selectList(any())).thenReturn(List.of(relation),List.of(member(1L),member(2L),member(3L)));
        var result = controller.myTeams(request(1L)).getData().get(0);
        assertThat(result.getMembers()).extracting("numberId").containsExactly("001","002","003");
        assertThat(new ObjectMapper().writeValueAsString(result)).doesNotContain("email","password","phone");
    }
}
