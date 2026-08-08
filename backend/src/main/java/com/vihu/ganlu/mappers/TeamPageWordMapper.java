package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.TeamPageWordEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TeamPageWordMapper {
    int insertTeamWord(TeamPageWordEntity e);

    List<TeamPageWordEntity> findAllWord(int id);

    int deleteTeamPageWordByIds(List<Integer> ids);

    int deleteTeamPageWordByIdsAndUserId(@Param("ids") List<Integer> ids, @Param("userId") Integer userId);

    // ---- 新增：按 teamId 查询 ----
    List<TeamPageWordEntity> findByTeamId(@Param("teamId") int teamId);

    List<TeamPageWordEntity> findByTeamIdAndStatus(@Param("teamId") int teamId, @Param("status") String status);

    TeamPageWordEntity findById(@Param("id") int id);

    TeamPageWordEntity findByIdForUpdate(@Param("id") int id);

    int archiveById(@Param("id") int id);

    int archiveByIdAndTeamId(@Param("id") int id, @Param("teamId") int teamId);

    int updateWordStatus(@Param("id") int id, @Param("status") String status, @Param("rejectReason") String rejectReason);

    int purgeById(@Param("id") int id);
}
