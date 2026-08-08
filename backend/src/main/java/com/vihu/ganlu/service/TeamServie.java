package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.TeamDetailDto;
import com.vihu.ganlu.entitys.TeamSaveRequest;
import com.vihu.ganlu.entitys.TeamYearSummary;

import java.util.List;
import java.util.Map;

public interface TeamServie {
    List<TeamYearSummary> getPublishedYears();

    Map<String, Object> getPublishedTeams(String year, int page, int size);

    TeamDetailDto getPublishedTeamDetail(int teamId);

    TeamDetailDto createTeam(TeamSaveRequest request);

    TeamDetailDto updateTeam(int teamId, TeamSaveRequest request);

    void archiveTeam(int teamId);
}
