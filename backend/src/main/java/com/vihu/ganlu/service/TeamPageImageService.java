package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.PublicImageUploadInfo;
import com.vihu.ganlu.entitys.TeamPageImageEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TeamPageImageService {
    int insertTeamImage(TeamPageImageEntity e);
    List<TeamPageImageEntity> findAllImages(int id);
    int deleteTeamPageImageByIds(List<Integer> ids);
    int deleteTeamPageImageByIdsAndUserId(List<Integer> ids, Integer userId);
    PublicImageUploadInfo stageTeamImage(MultipartFile imageFile, int uploaderUserId);
    void cancelStagedTeamImage(String token, int uploaderUserId);
    List<TeamPageImageEntity> findByTeamId(int teamId);
    List<TeamPageImageEntity> findByTeamIdAndStatus(int teamId, String status);
    TeamPageImageEntity findById(int id);
    boolean archiveById(int id);
    boolean archiveByIdAndTeamId(int id, int teamId);
    boolean updateStatus(int id, String status, String rejectReason);
    boolean purgeById(int id);
}
