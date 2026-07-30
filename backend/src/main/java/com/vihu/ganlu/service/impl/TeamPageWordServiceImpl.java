package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.TeamPageWordEntity;
import com.vihu.ganlu.mappers.TeamMediaMapper;
import com.vihu.ganlu.mappers.TeamPageWordMapper;
import com.vihu.ganlu.service.TeamPageWordService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
@Service
public class TeamPageWordServiceImpl implements TeamPageWordService {
    @Resource
    TeamPageWordMapper teamPageWordMapper;
    @Resource
    TeamMediaMapper teamMediaMapper;

    @Override
    public int insertTeamWord(TeamPageWordEntity e) {
        return teamPageWordMapper.insertTeamWord(e);
    }

    @Override
    public List<TeamPageWordEntity> findAllWord(int id) {
        return teamPageWordMapper.findAllWord(id);
    }

    @Override
    public int deleteTeamPageWordByIds(List<Integer> ids) {
        return teamPageWordMapper.deleteTeamPageWordByIds(ids);
    }

    @Override
    public int deleteTeamPageWordByIdsAndUserId(List<Integer> ids, Integer userId) {
        return teamPageWordMapper.deleteTeamPageWordByIdsAndUserId(ids, userId);
    }

    @Override
    public List<TeamPageWordEntity> findByTeamId(int teamId) {
        return teamPageWordMapper.findByTeamId(teamId);
    }

    @Override
    public List<TeamPageWordEntity> findByTeamIdAndStatus(int teamId, String status) {
        return teamPageWordMapper.findByTeamIdAndStatus(teamId, status);
    }

    @Override
    public TeamPageWordEntity findById(int id) {
        return teamPageWordMapper.findById(id);
    }

    @Override
    public boolean archiveById(int id) {
        int n = teamPageWordMapper.archiveById(id);
        if (n > 0) {
            teamMediaMapper.archiveByRelated("WORD", id); // 级联归档关联 media
        }
        return n > 0;
    }

    @Override
    public boolean archiveByIdAndTeamId(int id, int teamId) {
        int n = teamPageWordMapper.archiveByIdAndTeamId(id, teamId);
        if (n > 0) {
            teamMediaMapper.archiveByRelated("WORD", id); // 级联归档关联 media
        }
        return n > 0;
    }

    @Override
    public boolean updateStatus(int id, String status, String rejectReason) {
        int n = teamPageWordMapper.updateWordStatus(id, status, rejectReason);
        if (n > 0) {
            teamMediaMapper.updateStatusByRelated("WORD", id, status); // 级联同步 media 状态
        }
        return n > 0;
    }
}
