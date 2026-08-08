package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.TeamEntity;
import com.vihu.ganlu.entitys.TeamPageEntity;

public interface TeamPageService {
    TeamPageEntity findByTeamId(int teamId);

    TeamPageEntity ensureTeamPage(TeamEntity team);
}
