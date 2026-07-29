package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.TeamPageEntity;

import java.util.List;

public interface TeamPageService {
    List<TeamPageEntity> getTeamPage();
    List<TeamPageEntity> getTeamPageById(int id);
    void addTeamPage(TeamPageEntity e);
    void updateTeamPageById(TeamPageEntity e);
    void deleteTeamPageByIds(String[] ids);
}
