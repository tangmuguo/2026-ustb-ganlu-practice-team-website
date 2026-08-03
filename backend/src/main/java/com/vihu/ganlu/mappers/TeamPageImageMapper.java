package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.TeamPageImageEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TeamPageImageMapper {
    int insertTeamImage(TeamPageImageEntity e);

    List<TeamPageImageEntity> findAllImages(int id);

    int deleteTeamPageImageByIds(List<Integer> ids);

    int deleteTeamPageImageByIdsAndUserId(@Param("ids") List<Integer> ids, @Param("userId") Integer userId);

    // ---- 新增：按 teamId 查询 ----
    List<TeamPageImageEntity> findByTeamId(@Param("teamId") int teamId);

    List<TeamPageImageEntity> findByTeamIdAndStatus(@Param("teamId") int teamId, @Param("status") String status);

    TeamPageImageEntity findById(@Param("id") int id);

    int archiveById(@Param("id") int id);

    int archiveByIdAndTeamId(@Param("id") int id, @Param("teamId") int teamId);

    int updateImageStatus(@Param("id") int id, @Param("status") String status, @Param("rejectReason") String rejectReason);

    /**
     * 更新图片的 imageUrl 列。用于审核状态切换时把文件 move 后同步数据库相对路径
     * （images_pending/ ↔ images/）。
     */
    int updateImageUrl(@Param("id") int id, @Param("imageUrl") String imageUrl);

    int purgeById(@Param("id") int id);
}
