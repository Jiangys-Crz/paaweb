package com.cwnupaa.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.entity.SignupTeam;
import com.cwnupaa.backend.entity.Team;
import com.cwnupaa.backend.mapper.SignupTeamMapper;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** Source team ID identifies a team; existing event_id still owns the signup. */
@Service
public class SignupTeamIdentityService {
    private final SignupTeamMapper signups;

    public SignupTeamIdentityService(SignupTeamMapper signups) {
        this.signups = signups;
    }

    // Call after authorizing access to the source team, under the event row lock.
    public SignupTeam findForTeam(Long eventId, Team team) {
        SignupTeam existing = signups.selectOne(new QueryWrapper<SignupTeam>()
                .eq("event_id", eventId).eq("source_team_id", team.getTeamId()));
        SignupTeam sameName = signups.selectOne(new QueryWrapper<SignupTeam>()
                .eq("event_id", eventId).eq("team_name", team.getTeamName().trim()));
        if (sameName != null && !Objects.equals(sameName.getSignupId(), existing == null ? null : existing.getSignupId())) {
            if (sameName.getSourceTeamId() == null) {
                throw new IllegalArgumentException("该队名已有历史报名，尚未确认来源团队编号，请联系管理员核对");
            }
            throw new IllegalArgumentException("该队名已被其他团队编号的队伍使用，请更换队名");
        }
        return existing;
    }
}
