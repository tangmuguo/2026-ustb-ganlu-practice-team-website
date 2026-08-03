package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.TeamPageImageEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TeamPageImageMapper {
    int insertTeamImage(TeamPageImageEntity e);
    List<TeamPageImageEntity> findAllImages(int id);
    List<TeamPageImageEntity> findByTeamId(@Param("teamId") int teamId);
    List<TeamPageImageEntity> findByTeamIdAndStatus(
            @Param("teamId") int teamId, @Param("status") String status);
    TeamPageImageEntity findById(@Param("id") int id);
    TeamPageImageEntity findByIdForUpdate(@Param("id") int id);
    List<TeamPageImageEntity> findByIds(@Param("ids") List<Integer> ids);
    List<TeamPageImageEntity> findByIdsAndUserId(
            @Param("ids") List<Integer> ids, @Param("userId") Integer userId);
    int deleteTeamPageImageByIds(List<Integer> ids);
    int deleteTeamPageImageByIdsAndUserId(@Param("ids") List<Integer> ids, @Param("userId") Integer userId);
    int updateImageStatus(
            @Param("id") int id,
            @Param("status") String status,
            @Param("rejectReason") String rejectReason);
    int updateImageUrl(@Param("id") int id, @Param("imageUrl") String imageUrl);
    int archiveById(@Param("id") int id);
    int archiveByIdAndTeamId(@Param("id") int id, @Param("teamId") int teamId);
    int purgeById(@Param("id") int id);
}
