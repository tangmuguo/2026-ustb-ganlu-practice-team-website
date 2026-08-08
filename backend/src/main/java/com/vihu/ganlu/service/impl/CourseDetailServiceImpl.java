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
import com.vihu.ganlu.utils.MaterialFileValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;
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

    public CourseDetailServiceImpl(
            CourseDetailMapper courseDetailMapper,
            CourseService courseService,
            FileStorageUtil fileStorageUtil,
            MaterialFileValidator fileValidator,
            OfficePreviewService officePreviewService,
            MaterialUploadStorageService uploadStorageService) {
        this.courseDetailMapper = courseDetailMapper;
        this.courseService = courseService;
        this.fileStorageUtil = fileStorageUtil;
        this.fileValidator = fileValidator;
        this.officePreviewService = officePreviewService;
        this.uploadStorageService = uploadStorageService;
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

        String coverPath = null;
        String originalPath = null;
        String previewPath = null;
        try {
            coverPath = fileStorageUtil.moveInto(
                    cover.getPath(), COVER_ROOT, cover.getInfo().getExtension());
            originalPath = fileStorageUtil.moveInto(
                    materialFile.getPath(), ORIGINAL_ROOT, materialFile.getInfo().getExtension());
            Path storedOriginal = fileStorageUtil.loadFile(originalPath);

            String previewStatus = "READY";
            if ("ppt".equals(materialFile.getInfo().getExtension())
                    || "pptx".equals(materialFile.getInfo().getExtension())) {
                Path previewTarget = fileStorageUtil.createDirectory(PREVIEW_ROOT)
                        .resolve(UUID.randomUUID().toString() + ".pdf");
                try {
                    officePreviewService.convertToPdf(storedOriginal, previewTarget);
                    previewPath = fileStorageUtil.toRelativePath(previewTarget);
                } catch (IOException | RuntimeException conversionError) {
                    previewStatus = "FAILED";
                    log.warn("课件 {} 的预览转换失败: {}",
                            materialFile.getInfo().getOriginalName(), conversionError.getMessage());
                }
            } else {
                previewPath = fileStorageUtil.copyInto(
                        storedOriginal, PREVIEW_ROOT, materialFile.getInfo().getExtension());
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
            entity.setStatus(1);

            if (courseDetailMapper.insertCourseDetail(entity) != 1) {
                throw new IllegalStateException("保存课件记录失败");
            }
            uploadStorageService.consumeStagedFile(cover);
            uploadStorageService.consumeStagedFile(materialFile);
            decoratePublicFields(entity);
            return entity;
        } catch (RuntimeException | IOException e) {
            safeDelete(coverPath);
            safeDelete(originalPath);
            safeDelete(previewPath);
            discardMovedStagedMetadata(cover);
            discardMovedStagedMetadata(materialFile);
            throw e;
        }
    }

    @Override
    @Transactional
    public boolean deleteCourseById(int id) {
        CourseDetailEntity existing = courseDetailMapper.getCourseById(id);
        if (existing == null) {
            throw new NoSuchElementException("课件不存在");
        }
        if (courseDetailMapper.softDeleteCourseById(id) != 1) {
            return false;
        }
        safeDelete(existing.getOriginalFilePath());
        safeDelete(existing.getPreviewFilePath());
        safeDelete(existing.getThumbnailUrl());
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
            material.setUploaderName(material.getAuthor());
        }
    }

    private String displayName(UserEntity user) {
        if (StringUtils.hasText(user.getTeamname())) {
            return user.getTeamname().trim();
        }
        if (StringUtils.hasText(user.getRealname())) {
            return user.getRealname().trim();
        }
        return StringUtils.hasText(user.getUsername()) ? user.getUsername().trim() : "用户" + user.getId();
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

    private void discardMovedStagedMetadata(MaterialUploadStorageService.StagedFile staged) {
        if (staged == null || Files.isRegularFile(staged.getPath())) {
            return;
        }
        try {
            uploadStorageService.consumeStagedFile(staged);
        } catch (IOException cleanupError) {
            log.warn("清理已移动暂存文件的元数据失败: {}", cleanupError.getMessage());
        }
    }

    private void requireIdentifier(String identifier) {
        if (identifier == null || !FILE_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("文件标识不合法");
        }
    }

    private void requireToken(String token) {
        try {
            UUID.fromString(token);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("上传文件凭证不合法");
        }
    }
}
