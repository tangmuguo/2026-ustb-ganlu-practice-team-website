package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.TeamPageEntity;
import com.vihu.ganlu.mappers.TeamPageMapper;
import com.vihu.ganlu.service.TeamPageService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
@Service
public class TeamPageServiceImpl implements TeamPageService {
    @Resource
    TeamPageMapper teamPageMapper;
    @Override
    public List<TeamPageEntity> getTeamPage() {
        return teamPageMapper.getTeamPage();
    }

    @Override
    public List<TeamPageEntity> getTeamPageById(int id) {
        return teamPageMapper.getTeamPageById(id);
    }

    @Override
    public void addTeamPage(TeamPageEntity e) {
        teamPageMapper.addTeamPage(e);
    }

    @Override
    public void updateTeamPageById(TeamPageEntity e) {
        teamPageMapper.updateTeamPageById(e);
    }

    @Override
    public void deleteTeamPageByIds(String[] ids) {
        teamPageMapper.deleteTeamPageByIds(ids);
    }
}
