package com.cwnupaa.backend.vo;

import com.cwnupaa.backend.entity.ContestCategory;
import com.cwnupaa.backend.entity.Event;
import com.cwnupaa.backend.entity.Notice;
import com.cwnupaa.backend.entity.Training;

import java.util.List;

public class GlobalSearchVO {
    private List<Notice> notices;
    private List<ContestCategory> contestIntros;
    private List<Event> contestSignups;
    private List<ExternalBoardSummaryVO> contestBoards;
    private List<Training> trainings;
    private List<UserSearchVO> users;

    public List<Notice> getNotices() {
        return notices;
    }

    public void setNotices(List<Notice> notices) {
        this.notices = notices;
    }

    public List<ContestCategory> getContestIntros() {
        return contestIntros;
    }

    public void setContestIntros(List<ContestCategory> contestIntros) {
        this.contestIntros = contestIntros;
    }

    public List<Event> getContestSignups() {
        return contestSignups;
    }

    public void setContestSignups(List<Event> contestSignups) {
        this.contestSignups = contestSignups;
    }

    public List<ExternalBoardSummaryVO> getContestBoards() {
        return contestBoards;
    }

    public void setContestBoards(List<ExternalBoardSummaryVO> contestBoards) {
        this.contestBoards = contestBoards;
    }

    public List<Training> getTrainings() {
        return trainings;
    }

    public void setTrainings(List<Training> trainings) {
        this.trainings = trainings;
    }

    public List<UserSearchVO> getUsers() {
        return users;
    }

    public void setUsers(List<UserSearchVO> users) {
        this.users = users;
    }
}