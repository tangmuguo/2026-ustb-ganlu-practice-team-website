package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.TeamEntity;
import com.vihu.ganlu.entitys.TeamPageEntity;
import com.vihu.ganlu.mappers.TeamPageMapper;
import com.vihu.ganlu.service.TeamPageService;
import org.springframework.stereotype.Service;

@Service
public class TeamPageServiceImpl implements TeamPageService {
    private final TeamPageMapper teamPageMapper;

    public TeamPageServiceImpl(TeamPageMapper teamPageMapper) {
        this.teamPageMapper = teamPageMapper;
    }

    @Override
    public TeamPageEntity findByTeamId(int teamId) {
        return teamPageMapper.findByTeamId(teamId);
    }

    @Override
    public TeamPageEntity ensureTeamPage(TeamEntity team) {
        if (team == null || team.getId() == null) {
            throw new IllegalArgumentException("小队ID不能为空");
        }

        TeamPageEntity existing = teamPageMapper.findByTeamId(team.getId());
        TeamPageEntity desired = existing == null ? new TeamPageEntity() : existing;
        desired.setTeamId(team.getId());
        desired.setTitle(team.getName());
        desired.setStatus(toPageStatus(team.getStatus()));

        if (existing == null) {
            desired.setContent("");
            int inserted = teamPageMapper.insertTeamPage(desired);
            if (inserted != 1 || desired.getId() == null) {
                throw new IllegalStateException("创建小队详情页失败");
            }
        } else {
            teamPageMapper.updateMetadataByTeamId(desired);
        }
        return desired;
    }

    private TeamPageEntity.Status toPageStatus(TeamEntity.Status status) {
        if (status == TeamEntity.Status.PUBLISHED) {
            return TeamPageEntity.Status.PUBLISHED;
        }
        if (status == TeamEntity.Status.ARCHIVED) {
            return TeamPageEntity.Status.ARCHIVED;
        }
        return TeamPageEntity.Status.DRAFT;
    }
}
