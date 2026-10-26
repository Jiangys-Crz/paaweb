package com.cwnupaa.backend.service;

import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.vo.RankItemVO;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExternalBoardParticipantTypeTests {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void girlsReceiveOfficialRanksWhileStarAndNonRankingEntriesDoNot(boolean teamContest) {
        var service = new ExternalBoardService();
        var personal = mock(SignupPersonalMapper.class);
        var teams = mock(SignupTeamMapper.class);
        var details = mock(SignupTeamDetailMapper.class);
        var users = mock(UserMapper.class);
        ReflectionTestUtils.setField(service, "signupPersonalMapper", personal);
        ReflectionTestUtils.setField(service, "signupTeamMapper", teams);
        ReflectionTestUtils.setField(service, "signupTeamDetailMapper", details);
        ReflectionTestUtils.setField(service, "userMapper", users);
        var personalSignups = new ArrayList<SignupPersonal>();
        var teamSignups = new ArrayList<SignupTeam>();
        var members = new ArrayList<Users>();
        List<String> types = List.of("official", "girl", "star", "girl");
        for (int index = 0; index < types.size(); index++) {
            long id = index + 1;
            var individual = new SignupPersonal();
            individual.setUserId(id);
            individual.setType(types.get(index));
            individual.setIsRanking(id == 4 ? 0 : 1);
            personalSignups.add(individual);
            var team = new SignupTeam();
            team.setSignupId(id);
            team.setType(individual.getType());
            team.setIsRanking(individual.getIsRanking());
            teamSignups.add(team);
            var member = new Users();
            member.setUserId(id);
            member.setGender(id == 1 ? 1 : 2);
            members.add(member);
        }
        when(personal.selectList(any())).thenReturn(personalSignups);
        when(teams.selectList(any())).thenReturn(teamSignups);
        when(details.selectList(any())).thenReturn(List.of());
        when(users.selectBatchIds(any())).thenReturn(members);
        var event = new Event();
        event.setEventId(1L);
        event.setIsTeam(teamContest ? 1 : 0);
        var rows = new LinkedHashMap<Long, RankItemVO>();
        ReflectionTestUtils.invokeMethod(service, "prepareParticipantInfo", event, rows, false);
        ReflectionTestUtils.invokeMethod(service, "assignRanks", new ArrayList<>(rows.values()), true);
        assertThat(rows.get(1L).getIsStar()).isFalse();
        assertThat(rows.get(2L).getIsGirl()).isTrue();
        assertThat(rows.get(2L).getIsStar()).isFalse();
        assertThat(rows.get(2L).getRank()).isPositive();
        assertThat(rows.get(3L).getIsStar()).isTrue();
        assertThat(rows.get(3L).getRank()).isEqualTo(-1);
        assertThat(rows.get(4L).getIsStar()).isTrue();
        assertThat(rows.get(4L).getRank()).isEqualTo(-1);
    }
}
