package com.cwnupaa.backend.vo;

import com.cwnupaa.backend.entity.Team;

import java.util.List;

public class TeamVO extends Team {
    private String leaderName;
    private String myStatus;
    private String teamAvatarUrl;
    private String membersContent;
    private List<TeamMemberVO> members;

    public String getLeaderName() {
        return leaderName;
    }

    public void setLeaderName(String leaderName) {
        this.leaderName = leaderName;
    }

    public String getMyStatus() {
        return myStatus;
    }

    public void setMyStatus(String myStatus) {
        this.myStatus = myStatus;
    }

    public List<TeamMemberVO> getMembers() {
        return members;
    }

    public void setMembers(List<TeamMemberVO> members) {
        this.members = members;
    }

    public String getTeamAvatarUrl() {
        return teamAvatarUrl;
    }

    public void setTeamAvatarUrl(String teamAvatarUrl) {
        this.teamAvatarUrl = teamAvatarUrl;
    }

    public String getMembersContent() {
        return membersContent;
    }

    public void setMembersContent(String membersContent) {
        this.membersContent = membersContent;
    }

}
