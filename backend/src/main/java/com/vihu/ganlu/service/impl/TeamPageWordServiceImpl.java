package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.TeamPageWordEntity;
import com.vihu.ganlu.mappers.TeamPageWordMapper;
import com.vihu.ganlu.service.TeamPageWordService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class TeamPageWordServiceImpl implements TeamPageWordService {
    @Resource
    TeamPageWordMapper teamPageWordMapper;


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
}
