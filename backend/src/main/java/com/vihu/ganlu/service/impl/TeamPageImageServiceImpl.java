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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
        if (e == null) {
            return false;
        }
        // 先搬运文件（失败抛异常 → 事务回滚，DB 归档状态不变），再改 DB 状态
        moveImageByStatus(e, "ARCHIVED");
        int n = teamPageImageMapper.archiveById(id);
        if (n > 0 && e.getTeamId() != null) {
            teamMediaMapper.archiveByRelated("IMAGE", id, e.getTeamId()); // 级联归档关联 media
        }
        return n > 0;
    }

    @Override
    @Transactional
    public boolean archiveByIdAndTeamId(int id, int teamId) {
        TeamPageImageEntity e = teamPageImageMapper.findById(id);
        if (e == null || !Integer.valueOf(teamId).equals(e.getTeamId())) {
            return false;
        }
        moveImageByStatus(e, "ARCHIVED");
        int n = teamPageImageMapper.archiveByIdAndTeamId(id, teamId);
        if (n > 0) {
            teamMediaMapper.archiveByRelated("IMAGE", id, teamId); // 级联归档关联 media
        }
        return n > 0;
    }

    @Override
    @Transactional
    public boolean updateStatus(int id, String status, String rejectReason) {
        TeamPageImageEntity e = teamPageImageMapper.findById(id);
        if (e == null) {
            return false;
        }
        // 先搬运物理文件，成功后再 UPDATE DB 状态（Item 5 exy v4）：
        // 文件 move 不可被事务回滚，故必须保证「文件搬成功 ↔ DB 状态变更」原子可见。
        // moveImageByStatus 失败会抛 IllegalStateException，DB 状态保持不变，接口返回错误。
        moveImageByStatus(e, status);
        int n = teamPageImageMapper.updateImageStatus(id, status, rejectReason);
        if (n > 0) {
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
     * - 幂等：若文件已在目标目录，跳过 move。
     *
     * 失败策略（F1 收口 Item 5 exy v4：文件搬移不可进事务，需补偿保证文件/DB 最终一致）：
     * - 源目录无法识别 / 源文件不存在（历史数据从未 move 过，或已被清理）：
     *   属可接受场景，仅记 warn 并跳过——这类记录的物理文件本就不在预期位置，
     *   强行报错会让历史数据永远无法改状态。
     * - move 成功后立即注册事务回滚补偿钩子：若后续 DB 写失败导致事务回滚，
     *   afterCompletion(ROLLED_BACK) 把文件搬回原目录，保证「DB 回滚 → 文件也回滚」，
     *   避免未发布的 PENDING 文件滞留 images/（公开目录）被匿名访问。
     * - 补偿本身 IOException（极小概率）只 log.error 告警：此时事务已结束无法再回滚，
     *   文件在公开目录但 DB 状态已回滚为 PENDING，依赖后续对账修复——这是残留窗口，
     *   但有了钩子，正常 DB 异常路径已能自愈。
     * - 其它搬运失败（权限、目标已存在、ATOMIC_MOVE 不支持）抛 IllegalStateException，
     *   事务回滚，接口返回结构化错误（F8）。
     *
     * 路径分隔符：DB 中相对路径统一为 URL 风格（正斜杠）；兼容历史可能残留的反斜杠。
     */
    private void moveImageByStatus(TeamPageImageEntity e, String targetStatus) {
        if (e == null || e.getImageUrl() == null) {
            return;
        }
        // 标准化为正斜杠，兼容 Windows 历史数据（Item 1 exy v4）
        String currentUrl = e.getImageUrl().replace('\\', '/');
        String filename = currentUrl;
        int slash = currentUrl.lastIndexOf('/');
        if (slash >= 0 && slash < currentUrl.length() - 1) {
            filename = currentUrl.substring(slash + 1);
        }
        String targetDir = "PUBLISHED".equals(targetStatus) ? PUBLIC_DIR : PENDING_DIR;
        String sourceDir = currentUrl.startsWith(PENDING_DIR + "/") ? PENDING_DIR
                : currentUrl.startsWith(PUBLIC_DIR + "/") ? PUBLIC_DIR : null;

        // 源目录无法识别（历史数据可能是裸文件名），跳过 move，仅记 warn
        if (sourceDir == null) {
            log.warn("图片 imageUrl 目录前缀无法识别，跳过 move: id={}, url={}", e.getId(), currentUrl);
            return;
        }
        // 已在目标目录，无需 move
        if (sourceDir.equals(targetDir)) {
            return;
        }
        final String fromPath = sourceDir + "/" + filename;
        final String toPath = targetDir + "/" + filename;

        // 先检查源文件是否存在；不存在属历史数据场景，warn 跳过（不报错）
        try {
            if (!java.nio.file.Files.exists(fileStorageUtil.loadFile(fromPath))) {
                log.warn("图片源文件不存在，跳过 move（历史数据可能从未搬运过）: id={}, from={}",
                        e.getId(), fromPath);
                return;
            }
        } catch (Exception checkEx) {
            throw new IllegalStateException("图片搬运前检查源文件失败: id=" + e.getId()
                    + ", from=" + fromPath + ", reason=" + checkEx.getMessage(), checkEx);
        }

        try {
            fileStorageUtil.moveFile(fromPath, toPath);
        } catch (Exception moveEx) {
            // moveFile 内部已校验过源存在与目标冲突；能走到这里的失败都属真正异常，
            // 必须抛出，避免「DB 状态已变但文件没搬」的不一致被静默掩盖。
            throw new IllegalStateException("图片文件搬运失败: id=" + e.getId()
                    + ", " + fromPath + " -> " + toPath + ", reason=" + moveEx.getMessage(), moveEx);
        }
        // F1: move 成功后注册事务回滚补偿——若后续 DB 写失败导致事务回滚，
        // afterCompletion(ROLLED_BACK) 把文件搬回 fromPath，保证文件/DB 最终一致。
        registerRollbackCompensation(fromPath, toPath, e.getId());
        // 同步 imageUrl 列；此处失败也必须抛出，否则文件已搬而 DB 仍指旧路径
        if (teamPageImageMapper.updateImageUrl(e.getId(), toPath) != 1) {
            throw new IllegalStateException("同步图片 imageUrl 失败: id=" + e.getId() + ", url=" + toPath);
        }
        log.info("图片文件已搬运: id={}, {} -> {}", e.getId(), fromPath, toPath);
    }

    /**
     * 注册事务回滚补偿钩子：事务 ROLLED_BACK 时把文件从 toPath 搬回 fromPath。
     * 仅在事务内调用有效；无事务时（理论上不会，本服务方法都 @Transactional）注册会被忽略，
     * 此情况下 move 已成功且无事务可回滚，文件保持在新位置。
     */
    private void registerRollbackCompensation(String fromPath, String toPath, int imageId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 无事务上下文（防御性），不注册；move 已成功，按现状处理
            log.warn("move 成功但无事务上下文，无法注册回滚补偿: imageId={}, {} -> {}",
                    imageId, fromPath, toPath);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }
                // 事务回滚：DB 状态已恢复，但文件已搬到 toPath——搬回 fromPath 补偿
                try {
                    fileStorageUtil.moveFile(toPath, fromPath);
                    log.warn("事务回滚，已把图片搬回原目录补偿: imageId={}, {} -> {}",
                            imageId, toPath, fromPath);
                } catch (Exception rollbackEx) {
                    // 补偿失败：文件留在 toPath（可能是公开目录），DB 已回滚为 PENDING → 泄露窗口
                    // 事务已结束无法再回滚，只能 log.error 告警，依赖后续对账修复
                    log.error("事务回滚补偿失败！图片滞留目标目录，可能造成未发布内容泄露: imageId={}, stuck={}, reason={}",
                            imageId, toPath, rollbackEx.getMessage(), rollbackEx);
                }
            }
        });
    }
}
