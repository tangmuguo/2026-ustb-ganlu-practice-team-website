package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.TeamMediaEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TeamMediaMapper {
    int insertTeamMedia(TeamMediaEntity e);

    TeamMediaEntity findById(@Param("id") int id);

    List<TeamMediaEntity> findByTeamId(@Param("teamId") int teamId);

    List<TeamMediaEntity> findByStatus(@Param("teamId") int teamId, @Param("status") String status);

    List<TeamMediaEntity> findByRelated(@Param("relatedType") String relatedType, @Param("relatedId") int relatedId);

    int updateStatus(@Param("id") int id, @Param("status") String status, @Param("rejectReason") String rejectReason);

    int updateStatusByRelated(@Param("relatedType") String relatedType, @Param("relatedId") int relatedId,
                             @Param("status") String status, @Param("teamId") int teamId);

    int archiveByRelated(@Param("relatedType") String relatedType, @Param("relatedId") int relatedId,
                         @Param("teamId") int teamId);

    int deleteByIds(@Param("ids") List<Integer> ids);

    int deleteByIdsAndUploader(@Param("ids") List<Integer> ids, @Param("uploaderId") int uploaderId);

    int archiveByIdAndTeamId(@Param("id") int id, @Param("teamId") int teamId);

    int purgeById(@Param("id") int id);
}
