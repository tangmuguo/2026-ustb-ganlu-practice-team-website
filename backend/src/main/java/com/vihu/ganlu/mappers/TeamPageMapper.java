package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.TeamPageEntity;
import org.apache.ibatis.annotations.Param;

public interface TeamPageMapper {
    TeamPageEntity findById(@Param("id") int id);

    TeamPageEntity findByTeamId(@Param("teamId") int teamId);

    int insertTeamPage(TeamPageEntity teamPage);

    int updateMetadataByTeamId(TeamPageEntity teamPage);
}
