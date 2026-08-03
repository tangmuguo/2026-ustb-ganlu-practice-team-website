package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.TeamPageImageEntity;
import com.vihu.ganlu.mappers.TeamMediaMapper;
import com.vihu.ganlu.mappers.TeamPageImageMapper;
import com.vihu.ganlu.service.TeamPageImageService;
import com.vihu.ganlu.utils.FileStorageUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@Service
public class TeamPageImageServiceImpl implements TeamPageImageService {
    @Resource
    TeamPageImageMapper teamPageImageMapper;
    @Resource
    TeamMediaMapper teamMediaMapper;
    @Resource
    FileStorageUtil fileStorageUtil;

    @Value("${file.upload-dir}")
    private String uploadDir;

    // 私有目录（不映射静态资源）：新上传图片默认存放于此，PENDING/REJECTED/ARCHIVED 状态下不可匿名访问。
    private static final String PENDING_DIR = "images_pending";
    // 公开目录（被 CorsConfig 映射为 /images/**）：仅 PUBLISHED 图片物理文件位于此。
    private static final String PUBLIC_DIR = "images";

    @Override
    public int insertTeamImage(TeamPageImageEntity e) {
        return teamPageImageMapper.insertTeamImage(e);
    }

    @Override
    public List<TeamPageImageEntity> findAllImages(int id) {
        return teamPageImageMapper.findAllImages(id);
    }

    @Override
    public int deleteTeamPageImageByIds(List<Integer> ids) {
        return teamPageImageMapper.deleteTeamPageImageByIds(ids);
    }

    @Override
    public int deleteTeamPageImageByIdsAndUserId(List<Integer> ids, Integer userId) {
        return teamPageImageMapper.deleteTeamPageImageByIdsAndUserId(ids, userId);
    }

    public String uploadTeamImage(MultipartFile imageFile) {
        try {
            // 新上传图片默认存私有目录 images_pending/，审核通过后再 move 到 images/。
            // images_pending/ 不被 CorsConfig 映射为静态资源，PENDING/REJECTED/ARCHIVED 图片物理隔离。
            String thumbnailPath = fileStorageUtil.storeFile(imageFile, PENDING_DIR);
            return thumbnailPath;
        } catch (RuntimeException e) {
            throw new RuntimeException("文件存储失败", e);
        }
    }

    @Override
    public List<TeamPageImageEntity> findByTeamId(int teamId) {
        return teamPageImageMapper.findByTeamId(teamId);
    }

    @Override
    public List<TeamPageImageEntity> findByTeamIdAndStatus(int teamId, String status) {
        return teamPageImageMapper.findByTeamIdAndStatus(teamId, status);
    }

    @Override
    public TeamPageImageEntity findById(int id) {
        return teamPageImageMapper.findById(id);
    }

    @Override
    @Transactional
    public boolean archiveById(int id) {
        TeamPageImageEntity e = teamPageImageMapper.findById(id);
        int n = teamPageImageMapper.archiveById(id);
        if (n > 0 && e != null) {
            moveImageByStatus(e, "ARCHIVED");
            if (e.getTeamId() != null) {
                teamMediaMapper.archiveByRelated("IMAGE", id, e.getTeamId()); // 级联归档关联 media
            }
        }
        return n > 0;
    }

    @Override
    @Transactional
    public boolean archiveByIdAndTeamId(int id, int teamId) {
        int n = teamPageImageMapper.archiveByIdAndTeamId(id, teamId);
        if (n > 0) {
            TeamPageImageEntity e = teamPageImageMapper.findById(id);
            if (e != null) {
                moveImageByStatus(e, "ARCHIVED");
            }
            teamMediaMapper.archiveByRelated("IMAGE", id, teamId); // 级联归档关联 media
        }
        return n > 0;
    }

    @Override
    @Transactional
    public boolean updateStatus(int id, String status, String rejectReason) {
        TeamPageImageEntity e = teamPageImageMapper.findById(id);
        int n = teamPageImageMapper.updateImageStatus(id, status, rejectReason);
        if (n > 0 && e != null) {
            // 按目标状态搬运物理文件：PUBLISHED → 公开目录，其余 → 私有目录。
            // 这样 PENDING/REJECTED/ARCHIVED 图片无法通过 /images/** 静态地址匿名访问。
            moveImageByStatus(e, status);
            // 父内容被驳回/归档时，级联隐藏关联附件；父内容发布时不自动提升附件，
            // 附件需保持自身的独立审核结果，避免已驳回/已归档附件被复活。
            if (e.getTeamId() != null
                    && ("REJECTED".equals(status) || "ARCHIVED".equals(status))) {
                teamMediaMapper.updateStatusByRelated("IMAGE", id, status, e.getTeamId());
            }
        }
        return n > 0;
    }

    @Override
    public boolean updateImageUrl(int id, String imageUrl) {
        return teamPageImageMapper.updateImageUrl(id, imageUrl) > 0;
    }

    /**
     * 根据目标状态把图片物理文件在 images_pending/ 与 images/ 之间搬运，并同步 imageUrl 列。
     * - 目标 PUBLISHED：从 images_pending/ move 到 images/，前端 <img src> 通过 /images/** 公开访问。
     * - 目标 PENDING/REJECTED/ARCHIVED：从 images/ move 回 images_pending/，物理隔离防匿名访问。
     * - 幂等：若文件已在目标目录，跳过 move；若源文件不存在（历史数据），仅记 warn 不报错。
     *
     * 注意：文件 move 无法回滚 DB 事务，故采用「先 move 再 UPDATE」顺序；
     * 若 DB UPDATE 失败抛异常导致事务回滚，文件已搬至目标位置但 imageUrl 未更新，
     * 此时不一致状态为「文件已公开但 DB 仍指私有路径」——下次重新发布/驳回时会自愈，
     * 不会造成内容泄露或长期不可用。详见 Item 4 设计说明。
     */
    private void moveImageByStatus(TeamPageImageEntity e, String targetStatus) {
        if (e == null || e.getImageUrl() == null) {
            return;
        }
        String currentUrl = e.getImageUrl();
        String filename = currentUrl;
        // 提取纯文件名（去掉可能存在的目录前缀）
        int slash = currentUrl.lastIndexOf('/');
        if (slash >= 0 && slash < currentUrl.length() - 1) {
            filename = currentUrl.substring(slash + 1);
        }
        String targetDir = "PUBLISHED".equals(targetStatus) ? PUBLIC_DIR : PENDING_DIR;
        String sourceDir = currentUrl.startsWith(PENDING_DIR + "/") ? PENDING_DIR
                : currentUrl.startsWith(PUBLIC_DIR + "/") ? PUBLIC_DIR : null;

        // 源目录无法识别（历史数据可能是 images/ 或裸文件名），跳过 move，仅记 warn
        if (sourceDir == null) {
            log.warn("图片 imageUrl 目录前缀无法识别，跳过 move: id={}, url={}", e.getId(), currentUrl);
            return;
        }
        // 已在目标目录，无需 move
        if (sourceDir.equals(targetDir)) {
            return;
        }
        String fromPath = sourceDir + "/" + filename;
        String toPath = targetDir + "/" + filename;
        try {
            fileStorageUtil.moveFile(fromPath, toPath);
            teamPageImageMapper.updateImageUrl(e.getId(), toPath);
            log.info("图片文件已搬运: id={}, {} -> {}", e.getId(), fromPath, toPath);
        } catch (Exception ex) {
            // 源文件不存在（可能历史数据从未 move 过，或已被清理）属可接受场景，仅记 warn
            log.warn("图片文件搬运失败，可能源文件不存在: id={}, from={}, reason={}",
                    e.getId(), fromPath, ex.getMessage());
        }
    }
}
