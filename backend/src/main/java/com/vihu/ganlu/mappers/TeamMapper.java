package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.TeamEntity;

import java.util.List;

public interface TeamMapper {
    List<TeamEntity> getTeam();
    List<TeamEntity> getTeamById(int id);
    void addTeam(TeamEntity e);
    void updateTeamById(TeamEntity e);
    void deleteTeamByIds(String[] ids);
}
