package com.vihu.ganlu.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.vihu.ganlu.entitys.CourseDetailEntity;
import com.vihu.ganlu.entitys.CourseEntity;
import com.vihu.ganlu.entitys.MaterialCreateRequest;
import com.vihu.ganlu.entitys.MaterialSearchQuery;
import com.vihu.ganlu.entitys.UploadedFileInfo;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.mappers.CourseDetailMapper;
import com.vihu.ganlu.service.CourseDetailService;
import com.vihu.ganlu.service.CourseService;
import com.vihu.ganlu.service.OfficePreviewService;
import com.vihu.ganlu.utils.FileStorageUtil;
import com.vihu.ganlu.utils.GeneralCourseSubjectPolicy;
import com.vihu.ganlu.utils.MaterialFileValidator;
import com.vihu.ganlu.security.file.FileScanResult;
import com.vihu.ganlu.security.file.FileScanService;
import com.vihu.ganlu.security.file.ImageSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;
import java.util.UUID;

@Slf4j
@Service
public class CourseDetailServiceImpl implements CourseDetailService {
    private static final Pattern FILE_IDENTIFIER = Pattern.compile("^[a-fA-F0-9]{32}$");
    private static final String ORIGINAL_ROOT = "protected/materials";
    private static final String COVER_ROOT = "images/materials";
    private static final String PREVIEW_ROOT = "protected/material-previews";

    private final CourseDetailMapper courseDetailMapper;
    private final CourseService courseService;
    private final FileStorageUtil fileStorageUtil;
    private final MaterialFileValidator fileValidator;
    private final OfficePreviewService officePreviewService;
    private final MaterialUploadStorageService uploadStorageService;
    private final FileDeletionTaskService fileDeletionTaskService;
    private final FileScanService fileScanService;
    private final ImageSanitizer imageSanitizer;
    private final boolean secureFileGate;

    /** Legacy constructor retained for existing isolated lifecycle tests. */
    public CourseDetailServiceImpl(
            CourseDetailMapper courseDetailMapper,
            CourseService courseService,
            FileStorageUtil fileStorageUtil,
            MaterialFileValidator fileValidator,
            OfficePreviewService officePreviewService,
            MaterialUploadStorageService uploadStorageService,
            FileDeletionTaskService fileDeletionTaskService) {
        this(courseDetailMapper, courseService, fileStorageUtil, fileValidator,
                officePreviewService, uploadStorageService, fileDeletionTaskService,
                new FileScanService(path -> com.vihu.ganlu.security.file.MalwareScanner.ScanVerdict.CLEAN, 5000),
                ImageSanitizer.passthroughForTests(), false);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CourseDetailServiceImpl(
            CourseDetailMapper courseDetailMapper,
            CourseService courseService,
            FileStorageUtil fileStorageUtil,
            MaterialFileValidator fileValidator,
            OfficePreviewService officePreviewService,
            MaterialUploadStorageService uploadStorageService,
            FileDeletionTaskService fileDeletionTaskService,
            FileScanService fileScanService,
            ImageSanitizer imageSanitizer) {
        this(courseDetailMapper, courseService, fileStorageUtil, fileValidator,
                officePreviewService, uploadStorageService, fileDeletionTaskService,
                fileScanService, imageSanitizer, true);
    }

    private CourseDetailServiceImpl(
            CourseDetailMapper courseDetailMapper,
            CourseService courseService,
            FileStorageUtil fileStorageUtil,
            MaterialFileValidator fileValidator,
            OfficePreviewService officePreviewService,
            MaterialUploadStorageService uploadStorageService,
            FileDeletionTaskService fileDeletionTaskService,
            FileScanService fileScanService,
            ImageSanitizer imageSanitizer,
            boolean secureFileGate) {
        this.courseDetailMapper = courseDetailMapper;
        this.courseService = courseService;
        this.fileStorageUtil = fileStorageUtil;
        this.fileValidator = fileValidator;
        this.officePreviewService = officePreviewService;
        this.uploadStorageService = uploadStorageService;
        this.fileDeletionTaskService = fileDeletionTaskService;
        this.fileScanService = fileScanService;
        this.imageSanitizer = imageSanitizer;
        this.secureFileGate = secureFileGate;
    }

    @Override
    public PageInfo<CourseDetailEntity> search(MaterialSearchQuery query) {
        normalizeSearchQuery(query);
        PageHelper.startPage(query.getPage(), query.getPageSize());
        List<CourseDetailEntity> materials = courseDetailMapper.search(query);
        materials.forEach(this::decoratePublicFields);
        return new PageInfo<>(materials);
    }

    @Override
    public CourseDetailEntity getCourseById(int id) {
        CourseDetailEntity material = courseDetailMapper.getCourseById(id);
        if (material != null) {
            decoratePublicFields(material);
        }
        return material;
    }

    @Override
    @Transactional
    public CourseDetailEntity createMaterial(MaterialCreateRequest request, UserEntity uploader) throws IOException {
        validateCreateRequest(request, uploader);
        MaterialUploadStorageService.StagedFile cover = uploadStorageService.loadStagedFile(
                uploader.getId(), "COVER", request.getCoverToken());
        MaterialUploadStorageService.StagedFile materialFile = uploadStorageService.loadStagedFile(
                uploader.getId(), "MATERIAL", request.getFileToken());

        FileScanResult coverScan = null;
        FileScanResult materialScan = null;
        if (secureFileGate) {
            coverScan = scanService(cover.getPath(), "COURSE_COVER", uploader.getId());
            materialScan = scanService(materialFile.getPath(), "COURSE_MATERIAL", uploader.getId());
            if (!coverScan.isClean() || !materialScan.isClean()) {
                throw new com.vihu.ganlu.security.file.FileSecurityException(
                        "课件文件尚未通过安全扫描，保持隔离状态",
                        !coverScan.isClean() ? coverScan : materialScan);
            }
            imageSanitizer.sanitizeInPlace(cover.getPath(), cover.getInfo().getExtension());
            // Re-scan the normalized bytes, not the original metadata-bearing
            // upload. The clean verdict must describe the bytes that are
            // copied into the public cover path.
            coverScan = fileScanService.scan(cover.getPath(), "COURSE_COVER", uploader.getId());
            if (!coverScan.isClean()) {
                throw new com.vihu.ganlu.security.file.FileSecurityException(
                        "课件封面重编码后尚未通过安全扫描，保持隔离状态", coverScan);
            }
        }

        String coverPath = null;
        String originalPath = null;
        String previewPath = null;
        List<Long> recoveryTaskIds = new ArrayList<>();
        List<Long> committedFileTaskIds = new ArrayList<>();
        try {
            coverPath = fileStorageUtil.allocatePath(COVER_ROOT, cover.getInfo().getExtension());
            long coverRecoveryTask = fileDeletionTaskService.enqueueCourseOrphanCleanup(
                    coverPath, uploader.getId(), cover.getInfo().getSize());
            recoveryTaskIds.add(coverRecoveryTask);
            fileStorageUtil.copyToAllocatedPath(cover.getPath(), coverPath);
            if (secureFileGate) {
                requireScannedTarget(coverPath, "COURSE_COVER", uploader.getId());
            }

            originalPath = fileStorageUtil.allocatePath(ORIGINAL_ROOT, materialFile.getInfo().getExtension());
            long originalRecoveryTask = fileDeletionTaskService.enqueueCourseOrphanCleanup(
                    originalPath, uploader.getId(), materialFile.getInfo().getSize());
            recoveryTaskIds.add(originalRecoveryTask);
            fileStorageUtil.copyToAllocatedPath(materialFile.getPath(), originalPath);
            if (secureFileGate) {
                requireScannedTarget(originalPath, "COURSE_MATERIAL", uploader.getId());
            }
            Path storedOriginal = fileStorageUtil.loadFile(originalPath);

            String previewStatus = "READY";
            if ("ppt".equals(materialFile.getInfo().getExtension())
                    || "pptx".equals(materialFile.getInfo().getExtension())) {
                String allocatedPreviewPath = fileStorageUtil.allocatePath(PREVIEW_ROOT, "pdf");
                long previewRecoveryTask = fileDeletionTaskService.enqueueCourseOrphanCleanup(
                        allocatedPreviewPath, uploader.getId(), 0L);
                recoveryTaskIds.add(previewRecoveryTask);
                Path previewTarget = fileStorageUtil.loadFile(allocatedPreviewPath);
                try {
                    officePreviewService.convertToPdf(storedOriginal, previewTarget);
                    if (secureFileGate) {
                        requireScannedTarget(allocatedPreviewPath, "COURSE_PREVIEW", uploader.getId());
                    }
                    previewPath = allocatedPreviewPath;
                    committedFileTaskIds.add(previewRecoveryTask);
                } catch (IOException | RuntimeException conversionError) {
                    previewStatus = "FAILED";
                    safeDelete(allocatedPreviewPath);
                    log.warn("课件 {} 的预览转换失败: {}",
                            materialFile.getInfo().getOriginalName(), conversionError.getMessage());
                }
            } else {
                previewPath = fileStorageUtil.allocatePath(PREVIEW_ROOT, materialFile.getInfo().getExtension());
                long previewRecoveryTask = fileDeletionTaskService.enqueueCourseOrphanCleanup(
                        previewPath, uploader.getId(), materialFile.getInfo().getSize());
                recoveryTaskIds.add(previewRecoveryTask);
                fileStorageUtil.copyToAllocatedPath(storedOriginal, previewPath);
                if (secureFileGate) {
                    requireScannedTarget(previewPath, "COURSE_PREVIEW", uploader.getId());
                }
                committedFileTaskIds.add(previewRecoveryTask);
            }

            CourseDetailEntity entity = new CourseDetailEntity();
            entity.setTitle(request.getTitle().trim());
            entity.setCourseType(request.getCourseType());
            entity.setCourseId(request.getCourseType() == 1 ? request.getCourseId() : null);
            entity.setCustomSubject(request.getCourseType() == 2 ? request.getCustomSubject().trim() : null);
            entity.setYear(request.getYear());
            entity.setUploaderUserId(uploader.getId());
            entity.setUploaderName(displayName(uploader));
            entity.setThumbnailUrl(coverPath);
            entity.setOriginalFilePath(originalPath);
            entity.setPreviewFilePath(previewPath);
            entity.setOriginalFilename(materialFile.getInfo().getOriginalName());
            entity.setFileSize(materialFile.getInfo().getSize());
            entity.setFileExtension(materialFile.getInfo().getExtension());
            entity.setMimeType(materialFile.getInfo().getMimeType());
            entity.setPreviewStatus(previewStatus);
            if (secureFileGate) {
                entity.setScanStatus(FileSecurityStatusName.CLEAN);
                entity.setScanDiagnosticStatus(FileSecurityStatusName.CLEAN);
            }
            entity.setStatus(1);

            if (courseDetailMapper.insertCourseDetail(entity) != 1) {
                throw new IllegalStateException("保存课件记录失败");
            }
            committedFileTaskIds.add(coverRecoveryTask);
            committedFileTaskIds.add(originalRecoveryTask);
            registerCreateRecovery(cover, materialFile, recoveryTaskIds, committedFileTaskIds);
            decoratePublicFields(entity);
            return entity;
        } catch (RuntimeException e) {
            safeDelete(coverPath);
            safeDelete(originalPath);
            safeDelete(previewPath);
            throw e;
        }
    }

    @Override
    @Transactional
    public boolean deleteCourseById(int id, UserEntity actor) {
        CourseDetailEntity existing = courseDetailMapper.getCourseByIdForUpdate(id);
        if (existing == null) {
            throw new NoSuchElementException("课件不存在");
        }
        if (actor == null || actor.getId() == null || actor.getLevel() == null) {
            throw new SecurityException("无权删除该课件");
        }
        final int updated;
        if (actor.getLevel() == 0) {
            updated = courseDetailMapper.softDeleteCourseByIdForAdmin(id, actor.getId());
        } else if (actor.getLevel() == 1 && actor.getId().equals(existing.getUploaderUserId())) {
            updated = courseDetailMapper.softDeleteCourseByIdForOwner(id, actor.getId());
        } else {
            throw new SecurityException("无权删除该课件");
        }
        if (updated != 1) {
            return false;
        }
        fileDeletionTaskService.enqueueCourseFiles(existing);
        return true;
    }

    @Override
    public String saveChunk(MultipartFile chunk, int chunkNumber, int totalChunks, String identifier,
                            String filename, long expectedSize, String purpose, int userId) throws IOException {
        validateUploadParameters(chunkNumber, totalChunks, identifier, filename, expectedSize, purpose);
        return uploadStorageService.saveChunk(
                chunk, chunkNumber, totalChunks, identifier, filename, expectedSize, purpose, userId);
    }

    @Override
    public UploadedFileInfo mergeChunks(String filename, String identifier, int totalChunks,
                                        long expectedSize, String purpose, int userId) throws IOException {
        validateUploadParameters(1, totalChunks, identifier, filename, expectedSize, purpose);
        return uploadStorageService.mergeChunks(
                filename, identifier, totalChunks, expectedSize, purpose, userId);
    }

    @Override
    public Map<String, Object> checkFileExist(String fileMd5, String purpose, int userId) throws IOException {
        requireIdentifier(fileMd5);
        return uploadStorageService.checkFileExist(fileMd5, purpose, userId);
    }

    @Override
    public void cancelUpload(String identifier, String purpose, String token, int userId) throws IOException {
        if (identifier != null && !identifier.trim().isEmpty()) {
            requireIdentifier(identifier);
        }
        uploadStorageService.cancelUpload(userId, purpose, identifier, token);
    }

    @Override
    public Path getDownloadPath(int id) {
        CourseDetailEntity existing = courseDetailMapper.getCourseById(id);
        if (existing == null) {
            throw new NoSuchElementException("课件不存在");
        }
        Path path = fileStorageUtil.loadFile(existing.getOriginalFilePath());
        if (secureFileGate) requireClean(path, existing);
        if (!Files.isRegularFile(path)) {
            throw new NoSuchElementException("课件原文件不存在");
        }
        return path;
    }

    @Override
    public Path getPreviewPath(int id) {
        CourseDetailEntity existing = courseDetailMapper.getCourseById(id);
        if (existing == null) {
            throw new NoSuchElementException("课件不存在");
        }
        if (!"READY".equals(existing.getPreviewStatus())) {
            throw new NoSuchElementException("课件预览暂不可用");
        }
        Path path = fileStorageUtil.loadFile(existing.getPreviewFilePath());
        if (secureFileGate) requireClean(path, existing);
        if (!Files.isRegularFile(path)) {
            throw new NoSuchElementException("课件预览文件不存在");
        }
        return path;
    }

    private void validateCreateRequest(MaterialCreateRequest request, UserEntity uploader) {
        if (request == null || uploader == null || uploader.getId() == null) {
            throw new IllegalArgumentException("上传参数不完整");
        }
        if (!StringUtils.hasText(request.getTitle())
                || request.getTitle().trim().length() < 2
                || request.getTitle().trim().length() > 100) {
            throw new IllegalArgumentException("标题长度应为 2～100 字");
        }
        if (request.getCourseType() == null
                || (request.getCourseType() != 1 && request.getCourseType() != 2)) {
            throw new IllegalArgumentException("课程类型不合法");
        }
        int currentYear = Year.now().getValue();
        if (request.getYear() == null
                || request.getYear() < currentYear - 9
                || request.getYear() > currentYear) {
            throw new IllegalArgumentException("年份必须在最近 10 年内");
        }
        if (request.getCourseType() == 1) {
            if (request.getCourseId() == null) {
                throw new IllegalArgumentException("通识课程必须选择科目");
            }
            CourseEntity category = courseService.getCourseById(request.getCourseId());
            if (category == null || category.getStatus() == null || category.getStatus() != 1) {
                throw new IllegalArgumentException("所选科目不存在或已停用");
            }
            if (!GeneralCourseSubjectPolicy.isSupported(category.getCourseName())) {
                throw new IllegalArgumentException("通识课程仅支持语文、数学、英语");
            }
        } else if (!StringUtils.hasText(request.getCustomSubject())
                || request.getCustomSubject().trim().length() < 2
                || request.getCustomSubject().trim().length() > 30) {
            throw new IllegalArgumentException("特色课程科目长度应为 2～30 字");
        }
        requireToken(request.getCoverToken());
        requireToken(request.getFileToken());
    }

    private void normalizeSearchQuery(MaterialSearchQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("查询参数不能为空");
        }
        if (query.getPage() == null || query.getPage() < 1
                || query.getPageSize() == null || query.getPageSize() < 1 || query.getPageSize() > 50) {
            throw new IllegalArgumentException("分页参数不合法");
        }
        if (query.getCourseType() != null && query.getCourseType() != 1 && query.getCourseType() != 2) {
            throw new IllegalArgumentException("课程类型不合法");
        }
        int currentYear = Year.now().getValue();
        query.setMinYear(currentYear - 9);
        query.setMaxYear(currentYear);
        if (query.getYear() != null
                && (query.getYear() < query.getMinYear() || query.getYear() > query.getMaxYear())) {
            throw new IllegalArgumentException("年份必须在最近 10 年内");
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            if (keyword.length() > 50) {
                throw new IllegalArgumentException("关键词不能超过 50 字");
            }
            query.setKeyword(keyword);
        } else {
            query.setKeyword(null);
        }
    }

    private void validateUploadParameters(int chunkNumber, int totalChunks, String identifier,
                                          String filename, long expectedSize, String purpose) {
        requireIdentifier(identifier);
        fileValidator.normalizePurpose(purpose);
        if (!StringUtils.hasText(filename) || filename.length() > 255) {
            throw new IllegalArgumentException("文件名不合法");
        }
        if (chunkNumber < 1 || totalChunks < 1 || chunkNumber > totalChunks || totalChunks > 1000) {
            throw new IllegalArgumentException("分片参数不合法");
        }
        long maxSize = "COVER".equalsIgnoreCase(purpose)
                ? MaterialFileValidator.MAX_COVER_SIZE
                : MaterialFileValidator.MAX_MATERIAL_SIZE;
        if (expectedSize <= 0 || expectedSize > maxSize) {
            throw new IllegalArgumentException("文件大小不合法");
        }
    }

    private void decoratePublicFields(CourseDetailEntity material) {
        material.setPreviewUrl(StringUtils.hasText(material.getPreviewFilePath())
                ? "/courseDetail/materials/" + material.getId() + "/preview"
                : null);
        material.setDownloadUrl("/courseDetail/materials/" + material.getId() + "/download");
        if (!StringUtils.hasText(material.getUploaderName())) {
            material.setUploaderName(anonymousUploaderName(material));
        }

        // The mapper supplies only the reviewed public uploader projection.
        // Do not let legacy author data or the internal user key escape through
        // a public entity returned by this service.
        material.setAuthor(null);
        material.setUploaderUserId(null);
    }

    private String displayName(UserEntity user) {
        if (StringUtils.hasText(user.getTeamname())) {
            return user.getTeamname().trim();
        }
        if (StringUtils.hasText(user.getDisplayName())) {
            return user.getDisplayName().trim();
        }
        return user.getId() == null ? "匿名课件上传者" : "课件上传者#" + user.getId();
    }

    private String anonymousUploaderName(CourseDetailEntity material) {
        return material.getId() == null
                ? "匿名课件上传者"
                : "课件上传者#" + material.getId();
    }

    private void safeDelete(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return;
        }
        try {
            fileStorageUtil.deleteFile(relativePath);
        } catch (RuntimeException e) {
            log.warn("清理文件失败 {}: {}", relativePath, e.getMessage());
        }
    }

    private void registerCreateRecovery(
            MaterialUploadStorageService.StagedFile cover,
            MaterialUploadStorageService.StagedFile material,
            List<Long> recoveryTaskIds,
            List<Long> committedFileTaskIds) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("课件创建必须在数据库事务中执行");
        }
        List<Long> allTasks = new ArrayList<>(recoveryTaskIds);
        List<Long> committedTasks = new ArrayList<>(committedFileTaskIds);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                consumeAfterCommit(cover);
                consumeAfterCommit(material);
                for (Long taskId : committedTasks) cancelRecoveryTask(taskId);
                for (Long taskId : allTasks) {
                    if (!committedTasks.contains(taskId)) retryRecoveryTask(taskId);
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    for (Long taskId : allTasks) retryRecoveryTask(taskId);
                }
            }
        });
    }

    private void consumeAfterCommit(MaterialUploadStorageService.StagedFile staged) {
        try {
            uploadStorageService.consumeStagedFile(staged);
        } catch (IOException | RuntimeException error) {
            log.warn("课件已提交，但清理暂存文件失败，等待定时清理: {}", error.getMessage());
        }
    }

    private void cancelRecoveryTask(long taskId) {
        try {
            fileDeletionTaskService.cancelTask(taskId);
        } catch (RuntimeException error) {
            log.warn("取消课件孤儿清理任务失败，处理器将按有效引用保护文件: taskId={}", taskId, error);
        }
    }

    private void retryRecoveryTask(long taskId) {
        try {
            fileDeletionTaskService.retryNow(taskId);
        } catch (RuntimeException error) {
            log.warn("课件回滚文件暂未清理，等待持久化任务重试: taskId={}", taskId, error);
        }
    }

    private void requireIdentifier(String identifier) {
        if (identifier == null || !FILE_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("文件标识不合法");
        }
    }

    private FileScanResult scanService(Path path, String scope, int ownerId) {
        FileScanResult result = fileScanService.getLatest(path);
        if (!result.isClean()) result = fileScanService.scan(path, scope, ownerId);
        return result;
    }

    private void requireScannedTarget(String relativePath, String scope, int ownerId) {
        Path target = fileStorageUtil.loadFile(relativePath);
        FileScanResult result = fileScanService.scan(target, scope, ownerId);
        if (!result.isClean()) {
            throw new com.vihu.ganlu.security.file.FileSecurityException(
                    "正式文件尚未通过安全扫描，禁止公开或下载", result);
        }
    }

    private void requireClean(Path path, CourseDetailEntity existing) {
        if (!"CLEAN".equals(existing.getScanStatus())) {
            throw new com.vihu.ganlu.security.file.FileSecurityException(
                    "课件尚未通过安全扫描，禁止公开或下载");
        }
        fileScanService.requireClean(path);
    }

    private static final class FileSecurityStatusName {
        private static final String CLEAN = "CLEAN";
    }

    private void requireToken(String token) {
        try {
            UUID.fromString(token);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("上传文件凭证不合法");
        }
    }
}
