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

    /**
     * 公开端查询：只返回 PUBLISHED 且（无父内容 或 父内容 PUBLISHED 同 team）的附件。
     */
    List<TeamMediaEntity> findPublicByTeamId(int teamId);

    boolean updateStatus(int id, String status, String rejectReason);

    boolean archiveByRelated(String relatedType, int relatedId, int teamId);

    int deleteByIds(List<Integer> ids);

    int deleteByIdsAndUploader(List<Integer> ids, int uploaderId);

    boolean archiveByIdAndTeamId(int id, int teamId);
}
