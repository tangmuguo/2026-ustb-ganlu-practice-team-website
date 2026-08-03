package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.TeamPageImageEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TeamPageImageService {
    int insertTeamImage(TeamPageImageEntity e);

    List<TeamPageImageEntity> findAllImages(int id);

    int deleteTeamPageImageByIds(List<Integer> ids);

    int deleteTeamPageImageByIdsAndUserId(List<Integer> ids, Integer userId);

    String uploadTeamImage(MultipartFile imageFile);

    // ---- 新增 ----
    List<TeamPageImageEntity> findByTeamId(int teamId);

    List<TeamPageImageEntity> findByTeamIdAndStatus(int teamId, String status);

    TeamPageImageEntity findById(int id);

    boolean archiveById(int id);

    boolean archiveByIdAndTeamId(int id, int teamId);

    boolean updateStatus(int id, String status, String rejectReason);

    /**
     * 更新图片 imageUrl 列（文件 move 后同步数据库相对路径）。
     */
    boolean updateImageUrl(int id, String imageUrl);
}
