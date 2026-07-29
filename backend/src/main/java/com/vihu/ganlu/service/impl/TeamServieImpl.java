package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.TeamEntity;
import com.vihu.ganlu.mappers.TeamMapper;
import com.vihu.ganlu.service.TeamServie;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
@Service
public class TeamServieImpl implements TeamServie {
    @Resource
    TeamMapper teamMapper;

    @Override
    public List<TeamEntity> getTeam() {
        return teamMapper.getTeam();
    }

    @Override
    public List<TeamEntity> getTeamById(int id) {
        return teamMapper.getTeamById(id);
    }

    @Override
    public void addTeam(TeamEntity e) {
        teamMapper.addTeam(e);
    }

    @Override
    public void updateTeamById(TeamEntity e) {
        teamMapper.updateTeamById(e);
    }

    @Override
    public void deleteTeamByIds(String[] ids) {
        teamMapper.deleteTeamByIds(ids);
    }
}
