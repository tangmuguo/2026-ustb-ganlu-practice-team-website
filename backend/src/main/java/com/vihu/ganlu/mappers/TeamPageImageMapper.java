package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.TeamPageImageEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TeamPageImageMapper {
    int insertTeamImage(TeamPageImageEntity e);
    List<TeamPageImageEntity> findAllImages(int id);
    int deleteTeamPageImageByIds(List<Integer> ids);
    int deleteTeamPageImageByIdsAndUserId(@Param("ids") List<Integer> ids, @Param("userId") Integer userId);
}
