package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.TeamPageWordEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TeamPageWordMapper {
    int insertTeamWord(TeamPageWordEntity e);
    List<TeamPageWordEntity> findAllWord(int id);
    int deleteTeamPageWordByIds(List<Integer> ids);
    int deleteTeamPageWordByIdsAndUserId(@Param("ids") List<Integer> ids, @Param("userId") Integer userId);
}
