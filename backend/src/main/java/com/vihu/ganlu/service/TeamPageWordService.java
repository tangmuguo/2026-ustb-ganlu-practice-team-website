package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.TeamPageWordEntity;

import java.util.List;

public interface TeamPageWordService {
    int insertTeamWord(TeamPageWordEntity e);

    List<TeamPageWordEntity> findAllWord(int id);

    int deleteTeamPageWordByIds(List<Integer> ids);

    int deleteTeamPageWordByIdsAndUserId(List<Integer> ids, Integer userId);

    // ---- 新增 ----
    List<TeamPageWordEntity> findByTeamId(int teamId);

    List<TeamPageWordEntity> findByTeamIdAndStatus(int teamId, String status);

    TeamPageWordEntity findById(int id);

    boolean archiveById(int id);

    boolean archiveByIdAndTeamId(int id, int teamId);

    boolean updateStatus(int id, String status, String rejectReason);
}
