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
        TeamPageWordEntity e = teamPageWordMapper.findById(id);
        int n = teamPageWordMapper.archiveById(id);
        if (n > 0 && e != null && e.getTeamId() != null) {
            teamMediaMapper.archiveByRelated("WORD", id, e.getTeamId()); // 级联归档关联 media
        }
        return n > 0;
    }

    @Override
    public boolean archiveByIdAndTeamId(int id, int teamId) {
        int n = teamPageWordMapper.archiveByIdAndTeamId(id, teamId);
        if (n > 0) {
            teamMediaMapper.archiveByRelated("WORD", id, teamId); // 级联归档关联 media
        }
        return n > 0;
    }

    @Override
    public boolean updateStatus(int id, String status, String rejectReason) {
        TeamPageWordEntity e = teamPageWordMapper.findById(id);
        int n = teamPageWordMapper.updateWordStatus(id, status, rejectReason);
        // 父内容被驳回/归档时，级联隐藏关联附件；父内容发布时不自动提升附件，
        // 附件需保持自身的独立审核结果，避免已驳回/已归档附件被复活。
        if (n > 0 && e != null && e.getTeamId() != null
                && ("REJECTED".equals(status) || "ARCHIVED".equals(status))) {
            teamMediaMapper.updateStatusByRelated("WORD", id, status, e.getTeamId());
        }
        return n > 0;
    }
}
