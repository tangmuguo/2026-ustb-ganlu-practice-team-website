package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.TeamEntity;

import java.util.List;

public interface TeamServie {
    List<TeamEntity> getTeam();
    List<TeamEntity> getTeamById(int id);
    void addTeam(TeamEntity e);
    void updateTeamById(TeamEntity e);
    void deleteTeamByIds(String[] ids);
}
