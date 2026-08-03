package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.TeamMediaEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TeamMediaMapper {
    int insertTeamMedia(TeamMediaEntity e);

    TeamMediaEntity findById(@Param("id") int id);

    List<TeamMediaEntity> findByTeamId(@Param("teamId") int teamId);

    List<TeamMediaEntity> findByStatus(@Param("teamId") int teamId, @Param("status") String status);

    /**
     * 公开端查询：只返回 media 状态 PUBLISHED，且（无父内容 或 父内容 PUBLISHED 且同 team）的记录。
     * 解决 Item 6：避免列表展示但下载 404（父内容未发布时不该出现在公开列表）。
     */
    List<TeamMediaEntity> findPublicByTeamId(@Param("teamId") int teamId);

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
