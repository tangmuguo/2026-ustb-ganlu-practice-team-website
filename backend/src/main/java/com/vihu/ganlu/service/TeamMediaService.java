package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.TeamMediaEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TeamMediaService {
    TeamMediaEntity uploadMedia(MultipartFile file, int uploaderId, int teamId,
                                String relatedType, Integer relatedId);

    TeamMediaEntity findById(int id);

    List<TeamMediaEntity> findByTeamId(int teamId);

    List<TeamMediaEntity> findByStatus(int teamId, String status);

    boolean updateStatus(int id, String status, String rejectReason);

    boolean archiveByRelated(String relatedType, int relatedId);

    int deleteByIds(List<Integer> ids);

    int deleteByIdsAndUploader(List<Integer> ids, int uploaderId);
}
