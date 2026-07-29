package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.TeamPageEntity;

import java.util.List;

public interface TeamPageMapper {
    List<TeamPageEntity> getTeamPage();
    List<TeamPageEntity> getTeamPageById(int id);
    void addTeamPage(TeamPageEntity e);
    void updateTeamPageById(TeamPageEntity e);
    void deleteTeamPageByIds(String[] ids);

}
