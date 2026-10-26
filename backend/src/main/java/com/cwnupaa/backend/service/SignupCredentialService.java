package com.cwnupaa.backend.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.cwnupaa.backend.entity.SignupPersonal;
import com.cwnupaa.backend.entity.SignupTeam;
import com.cwnupaa.backend.entity.SignupTeamDetail;
import com.cwnupaa.backend.mapper.SignupPersonalMapper;
import com.cwnupaa.backend.mapper.SignupTeamMapper;
import com.cwnupaa.backend.mapper.SignupTeamDetailMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Revoke credentials in the same transaction that rejects/cancels the signup. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class SignupCredentialService {
    private final SignupPersonalMapper personal;
    private final SignupTeamMapper teams;
    private final SignupTeamDetailMapper details;

    public SignupCredentialService(SignupPersonalMapper personal, SignupTeamMapper teams, SignupTeamDetailMapper details) {
        this.personal = personal;
        this.teams = teams;
        this.details = details;
    }

    public void clearPersonal(Long signupId) {
        // Explicit SET NULL: updateById skips null fields under the default strategy.
        personal.update(null, cleared(new UpdateWrapper<SignupPersonal>().eq("signup_id", signupId)));
    }

    public void clearTeam(Long signupId) {
        teams.update(null, cleared(new UpdateWrapper<SignupTeam>().eq("signup_id", signupId)));
        details.update(null, cleared(new UpdateWrapper<SignupTeamDetail>().eq("signup_team_id", signupId)));
    }

    private <T> UpdateWrapper<T> cleared(UpdateWrapper<T> update) {
        return update.set("contest_account", null).set("contest_password", null).set("seat_number", null);
    }
}
