package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.TeamPageWordEntity;

import java.util.List;

public interface TeamPageWordService {
    int insertTeamWord(TeamPageWordEntity e);
    List<TeamPageWordEntity> findAllWord(int id);
    int deleteTeamPageWordByIds(List<Integer> ids);
    int deleteTeamPageWordByIdsAndUserId(List<Integer> ids, Integer userId);
}
