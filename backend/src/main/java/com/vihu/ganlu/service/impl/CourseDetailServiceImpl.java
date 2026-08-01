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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class CourseDetailServiceImpl implements CourseDetailService {
    private static final Pattern FILE_IDENTIFIER = Pattern.compile("^[a-fA-F0-9]{32}$");
    private static final String CHUNK_ROOT = "temp_chunks";
    private static final String STAGING_ROOT = "staging/materials";
    private static final String ORIGINAL_ROOT = "protected/materials";
    private static final String COVER_ROOT = "images/materials";
    private static final String PREVIEW_ROOT = "materials/previews";

    private final CourseDetailMapper courseDetailMapper;
    private final CourseService courseService;
    private final FileStorageUtil fileStorageUtil;
    private final MaterialFileValidator fileValidator;
    private final OfficePreviewService officePreviewService;

    public CourseDetailServiceImpl(
            CourseDetailMapper courseDetailMapper,
            CourseService courseService,
            FileStorageUtil fileStorageUtil,
            MaterialFileValidator fileValidator,
            OfficePreviewService officePreviewService) {
        this.courseDetailMapper = courseDetailMapper;
        this.courseService = courseService;
        this.fileStorageUtil = fileStorageUtil;
        this.fileValidator = fileValidator;
        this.officePreviewService = officePreviewService;
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
        StagedFile cover = loadStagedFile(uploader.getId(), "COVER", request.getCoverToken());
        StagedFile materialFile = loadStagedFile(uploader.getId(), "MATERIAL", request.getFileToken());

        String coverPath = null;
        String originalPath = null;
        String previewPath = null;
        try {
            coverPath = fileStorageUtil.moveInto(cover.path, COVER_ROOT, cover.info.getExtension());
            originalPath = fileStorageUtil.moveInto(materialFile.path, ORIGINAL_ROOT, materialFile.info.getExtension());
            Path storedOriginal = fileStorageUtil.loadFile(originalPath);

            String previewStatus = "READY";
            if ("ppt".equals(materialFile.info.getExtension()) || "pptx".equals(materialFile.info.getExtension())) {
                Path previewTarget = fileStorageUtil.createDirectory(PREVIEW_ROOT)
                        .resolve(UUID.randomUUID().toString() + ".pdf");
                try {
                    officePreviewService.convertToPdf(storedOriginal, previewTarget);
                    previewPath = fileStorageUtil.toRelativePath(previewTarget);
                } catch (IOException | RuntimeException conversionError) {
                    previewStatus = "FAILED";
                    log.warn("课件 {} 的预览转换失败: {}",
                            materialFile.info.getOriginalName(), conversionError.getMessage());
                }
            } else {
                previewPath = fileStorageUtil.copyInto(
                        storedOriginal, PREVIEW_ROOT, materialFile.info.getExtension());
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
            entity.setOriginalFilename(materialFile.info.getOriginalName());
            entity.setFileSize(materialFile.info.getSize());
            entity.setFileExtension(materialFile.info.getExtension());
            entity.setMimeType(materialFile.info.getMimeType());
            entity.setPreviewStatus(previewStatus);
            entity.setStatus(1);

            if (courseDetailMapper.insertCourseDetail(entity) != 1) {
                throw new IllegalStateException("保存课件记录失败");
            }
            Files.deleteIfExists(cover.metadata);
            Files.deleteIfExists(materialFile.metadata);
            decoratePublicFields(entity);
            return entity;
        } catch (RuntimeException | IOException e) {
            safeDelete(coverPath);
            safeDelete(originalPath);
            safeDelete(previewPath);
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
        if (chunk == null || chunk.isEmpty() || chunk.getSize() > MaterialFileValidator.MAX_CHUNK_SIZE) {
            throw new IllegalArgumentException("分片大小不合法");
        }
        Path directory = chunkDirectory(userId, purpose, identifier);
        Path target = directory.resolve(chunkNumber + ".part").normalize();
        try (InputStream input = chunk.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return String.valueOf(chunkNumber);
    }

    @Override
    public UploadedFileInfo mergeChunks(String filename, String identifier, int totalChunks,
                                        long expectedSize, String purpose, int userId) throws IOException {
        validateUploadParameters(1, totalChunks, identifier, filename, expectedSize, purpose);
        String normalizedPurpose = fileValidator.normalizePurpose(purpose);
        UploadedFileInfo existing = findStagedFileByChecksum(userId, normalizedPurpose, identifier);
        if (existing != null) {
            return existing;
        }

        Path directory = chunkDirectory(userId, normalizedPurpose, identifier);
        Path merged = directory.resolve(identifier + ".merge").normalize();
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(merged))) {
            for (int chunkNumber = 1; chunkNumber <= totalChunks; chunkNumber++) {
                Path part = directory.resolve(chunkNumber + ".part");
                if (!Files.isRegularFile(part)) {
                    throw new IllegalStateException("缺少第 " + chunkNumber + " 个分片");
                }
                Files.copy(part, output);
            }
        }

        UploadedFileInfo info;
        try {
            info = fileValidator.validate(merged, filename, normalizedPurpose, expectedSize);
            if (!identifier.equalsIgnoreCase(info.getChecksum())) {
                throw new IllegalArgumentException("文件 MD5 校验失败");
            }
        } catch (RuntimeException | IOException e) {
            Files.deleteIfExists(merged);
            fileStorageUtil.deleteTree(directory);
            throw e;
        }

        String token = UUID.randomUUID().toString();
        info.setToken(token);
        Path stagingDirectory = fileStorageUtil.createDirectory(stagingDirectory(userId, normalizedPurpose));
        Path stagedFile = stagingDirectory.resolve(token + "." + info.getExtension()).normalize();
        Files.move(merged, stagedFile, StandardCopyOption.REPLACE_EXISTING);
        saveMetadata(stagingDirectory.resolve(token + ".properties"), info);
        fileStorageUtil.deleteTree(directory);
        return info;
    }

    @Override
    public Map<String, Object> checkFileExist(String fileMd5, String purpose, int userId) throws IOException {
        requireIdentifier(fileMd5);
        String normalizedPurpose = fileValidator.normalizePurpose(purpose);
        Map<String, Object> result = new HashMap<>();
        UploadedFileInfo staged = findStagedFileByChecksum(userId, normalizedPurpose, fileMd5);
        if (staged != null) {
            result.put("complete", true);
            result.put("file", staged);
            result.put("uploadedChunks", Collections.emptyList());
            return result;
        }

        Path directory = fileStorageUtil.createDirectory(chunkDirectoryName(userId, normalizedPurpose, fileMd5));
        List<Integer> uploadedChunks = new ArrayList<>();
        try (Stream<Path> paths = Files.list(directory)) {
            uploadedChunks = paths
                    .filter(path -> path.getFileName().toString().matches("\\d+\\.part"))
                    .map(path -> path.getFileName().toString().replace(".part", ""))
                    .map(Integer::valueOf)
                    .sorted()
                    .collect(Collectors.toList());
        }
        result.put("complete", false);
        result.put("uploadedChunks", uploadedChunks);
        return result;
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

    private Path chunkDirectory(int userId, String purpose, String identifier) {
        return fileStorageUtil.createDirectory(chunkDirectoryName(userId, purpose, identifier));
    }

    private String chunkDirectoryName(int userId, String purpose, String identifier) {
        return CHUNK_ROOT + "/" + userId + "/" + fileValidator.normalizePurpose(purpose).toLowerCase()
                + "/" + identifier.toLowerCase();
    }

    private String stagingDirectory(int userId, String purpose) {
        return STAGING_ROOT + "/" + userId + "/" + purpose.toLowerCase();
    }

    private StagedFile loadStagedFile(int userId, String purpose, String token) throws IOException {
        requireToken(token);
        Path directory = fileStorageUtil.createDirectory(stagingDirectory(userId, purpose));
        Path metadata = directory.resolve(token + ".properties");
        if (!Files.isRegularFile(metadata)) {
            throw new IllegalArgumentException("上传文件凭证不存在或已过期");
        }
        UploadedFileInfo info = readMetadata(metadata);
        if (!purpose.equals(info.getPurpose())) {
            throw new IllegalArgumentException("上传文件用途不匹配");
        }
        Path file = directory.resolve(token + "." + info.getExtension()).normalize();
        UploadedFileInfo verified = fileValidator.validate(file, info.getOriginalName(), purpose, info.getSize());
        if (!verified.getChecksum().equalsIgnoreCase(info.getChecksum())) {
            throw new IllegalArgumentException("暂存文件校验失败");
        }
        return new StagedFile(file, metadata, info);
    }

    private UploadedFileInfo findStagedFileByChecksum(int userId, String purpose, String checksum) throws IOException {
        Path directory = fileStorageUtil.createDirectory(stagingDirectory(userId, purpose));
        try (Stream<Path> paths = Files.list(directory)) {
            List<Path> metadataFiles = paths
                    .filter(path -> path.getFileName().toString().endsWith(".properties"))
                    .collect(Collectors.toList());
            for (Path metadata : metadataFiles) {
                UploadedFileInfo info = readMetadata(metadata);
                if (checksum.equalsIgnoreCase(info.getChecksum()) && purpose.equals(info.getPurpose())) {
                    Path file = directory.resolve(info.getToken() + "." + info.getExtension());
                    if (Files.isRegularFile(file)) {
                        return info;
                    }
                }
            }
        }
        return null;
    }

    private void saveMetadata(Path metadata, UploadedFileInfo info) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("token", info.getToken());
        properties.setProperty("originalName", info.getOriginalName());
        properties.setProperty("extension", info.getExtension());
        properties.setProperty("mimeType", info.getMimeType());
        properties.setProperty("checksum", info.getChecksum());
        properties.setProperty("size", String.valueOf(info.getSize()));
        properties.setProperty("purpose", info.getPurpose());
        try (OutputStream output = Files.newOutputStream(metadata)) {
            properties.store(output, "Ganlu material staged upload");
        }
    }

    private UploadedFileInfo readMetadata(Path metadata) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(metadata)) {
            properties.load(input);
        }
        UploadedFileInfo info = new UploadedFileInfo();
        info.setToken(properties.getProperty("token"));
        info.setOriginalName(properties.getProperty("originalName"));
        info.setExtension(properties.getProperty("extension"));
        info.setMimeType(properties.getProperty("mimeType"));
        info.setChecksum(properties.getProperty("checksum"));
        info.setSize(Long.parseLong(properties.getProperty("size", "0")));
        info.setPurpose(properties.getProperty("purpose"));
        return info;
    }

    private void decoratePublicFields(CourseDetailEntity material) {
        material.setPreviewUrl(StringUtils.hasText(material.getPreviewFilePath())
                ? "/" + material.getPreviewFilePath().replace('\\', '/')
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

    private static class StagedFile {
        private final Path path;
        private final Path metadata;
        private final UploadedFileInfo info;

        private StagedFile(Path path, Path metadata, UploadedFileInfo info) {
            this.path = path;
            this.metadata = metadata;
            this.info = info;
        }
    }
}
