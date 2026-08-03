package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.PublicImageAssetEntity;
import com.vihu.ganlu.entitys.PublicImageUploadInfo;
import com.vihu.ganlu.entitys.TeamPageImageEntity;
import com.vihu.ganlu.mappers.PublicImageQuotaMapper;
import com.vihu.ganlu.mappers.TeamPageImageMapper;
import com.vihu.ganlu.service.impl.PublicImageLifecycleService;
import com.vihu.ganlu.service.impl.TeamPageImageServiceImpl;
import com.vihu.ganlu.utils.FileStorageUtil;
import com.vihu.ganlu.utils.PublicImageValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TeamPageImageUploadSecurityTests {
    private static final int UPLOADER_ID = 7;

    @TempDir
    Path uploadRoot;

    private TeamPageImageMapper mapper;
    private PublicImageQuotaMapper quotaMapper;
    private FileStorageUtil fileStorageUtil;
    private PublicImageLifecycleService lifecycleService;
    private TeamPageImageServiceImpl service;
    private AtomicInteger permanentFileCount;
    private AtomicLong permanentBytes;
    private Map<String, PublicImageAssetEntity> permanentAssets;

    @BeforeEach
    void setUp() {
        service = serviceWithLimits(1024 * 1024, 10);
    }

    @Test
    void acceptsAndStoresDecodedPngWithControlledExtension() throws Exception {
        byte[] png = imageBytes("png");
        String path = stageAndSave(file("team.png", "image/png", png));

        assertTrue(path.matches("images/" + UPLOADER_ID + "/[0-9a-f-]+\\.png"));
        assertArrayEquals(png, Files.readAllBytes(uploadRoot.resolve(path)));
    }

    @Test
    void acceptsAndStoresDecodedJpegAsJpg() throws Exception {
        byte[] jpeg = imageBytes("jpg");
        String path = stageAndSave(file("team.jpeg", "image/jpeg", jpeg));

        assertTrue(path.matches("images/" + UPLOADER_ID + "/[0-9a-f-]+\\.jpg"));
        assertArrayEquals(jpeg, Files.readAllBytes(uploadRoot.resolve(path)));
    }

    @Test
    void acceptsAndStoresValidatedWebp() throws Exception {
        byte[] webp = validWebp();
        String path = stageAndSave(file("team.webp", "image/webp", webp));

        assertTrue(path.matches("images/" + UPLOADER_ID + "/[0-9a-f-]+\\.webp"));
        assertArrayEquals(webp, Files.readAllBytes(uploadRoot.resolve(path)));
    }

    @Test
    void rejectsHtmlAndLeavesNoPublicFile() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.stageTeamImage(
                file("attack.html", "text/html", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)), UPLOADER_ID));
        assertNoStoredFiles();
    }

    @Test
    void rejectsSvgAndLeavesNoPublicFile() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.stageTeamImage(
                file("attack.svg", "image/svg+xml", "<svg onload='alert(1)'></svg>".getBytes(StandardCharsets.UTF_8)), UPLOADER_ID));
        assertNoStoredFiles();
    }

    @Test
    void rejectsTextDisguisedAsJpegAndLeavesNoPublicFile() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.stageTeamImage(
                file("fake.jpg", "image/jpeg", "not a jpeg".getBytes(StandardCharsets.UTF_8)), UPLOADER_ID));
        assertNoStoredFiles();
    }

    @Test
    void rejectsWebpWithTrailingDataAndLeavesNoPublicFile() throws Exception {
        byte[] webp = validWebp();
        byte[] withTrailingData = Arrays.copyOf(webp, webp.length + 1);

        assertThrows(IllegalArgumentException.class, () -> service.stageTeamImage(
                file("invalid.webp", "image/webp", withTrailingData), UPLOADER_ID));
        assertNoStoredFiles();
    }

    @Test
    void rejectsFileOverConfiguredLimitAndLeavesNoPublicFile() throws Exception {
        TeamPageImageServiceImpl limitedService = serviceWithLimits(32, 10);
        assertThrows(IllegalArgumentException.class, () -> limitedService.stageTeamImage(
                file("large.png", "image/png", imageBytes("png")), UPLOADER_ID));
        assertNoStoredFiles();
    }

    @Test
    void removesUnconsumedStagedUploadAfterTtl() throws Exception {
        PublicImageUploadInfo upload = service.stageTeamImage(
                file("temporary.png", "image/png", imageBytes("png")), UPLOADER_ID);
        Path stagedFile = stagedPath(upload);
        Files.setLastModifiedTime(stagedFile, FileTime.fromMillis(System.currentTimeMillis() - 2 * 60 * 60 * 1000L));

        lifecycleService.cleanupExpiredUploads();

        assertFalse(Files.exists(stagedFile));
        assertNoPublicFiles();
    }

    @Test
    void enforcesPerUserStagedFileQuota() throws Exception {
        TeamPageImageServiceImpl limitedService = serviceWithLimits(1024 * 1024, 1);
        limitedService.stageTeamImage(file("first.png", "image/png", imageBytes("png")), UPLOADER_ID);

        assertThrows(IllegalStateException.class, () -> limitedService.stageTeamImage(
                file("second.png", "image/png", imageBytes("png")), UPLOADER_ID));
        assertEquals(1, regularFileCount(uploadRoot.resolve("staging/public-images/" + UPLOADER_ID)));
    }

    @Test
    void permanentQuotaSurvivesPromotionAndDeletionReleasesIt() throws Exception {
        service = serviceWithLimits(1024 * 1024, 10, 1, 0);
        String firstPath = stageAndSave(file("first.png", "image/png", imageBytes("png")));
        PublicImageUploadInfo secondUpload = service.stageTeamImage(
                file("second.png", "image/png", imageBytes("png")), UPLOADER_ID);
        TeamPageImageEntity secondImage = imageFromUpload(secondUpload);

        assertThrows(IllegalStateException.class, () -> service.insertTeamImage(secondImage));
        assertTrue(Files.exists(stagedPath(secondUpload)));
        assertEquals(1, permanentFileCount.get());

        TeamPageImageEntity firstImage = new TeamPageImageEntity();
        firstImage.setId(41);
        firstImage.setImageUrl(firstPath);
        when(mapper.findByIds(Collections.singletonList(41))).thenReturn(Collections.singletonList(firstImage));
        when(mapper.deleteTeamPageImageByIds(Collections.singletonList(41))).thenReturn(1);
        assertEquals(1, service.deleteTeamPageImageByIds(Collections.singletonList(41)));

        assertEquals(0, permanentFileCount.get());
        assertFalse(Files.exists(uploadRoot.resolve(firstPath)));
        assertEquals(1, service.insertTeamImage(secondImage));
        assertEquals(1, permanentFileCount.get());
        assertTrue(Files.exists(uploadRoot.resolve(secondImage.getImageUrl())));
    }

    @Test
    void rejectsUploadWhenGlobalFreeDiskThresholdWouldBeCrossed() throws Exception {
        FileStorageUtil lowSpaceStorage = spy(new FileStorageUtil(uploadRoot.toString(), "test"));
        doReturn(0L).when(lowSpaceStorage).getUsableSpace();
        service = serviceWithStorage(lowSpaceStorage, 1024 * 1024, 10, 10, 1);

        assertThrows(IllegalStateException.class, () -> service.stageTeamImage(
                file("no-space.png", "image/png", imageBytes("png")), UPLOADER_ID));
        assertNoStoredFiles();
    }

    @Test
    void deletesPhysicalImageAfterRecordDeletion() throws Exception {
        String imageUrl = fileStorageUtil.storeFile(
                file("saved.png", "image/png", imageBytes("png")), "images", "png");
        TeamPageImageEntity image = new TeamPageImageEntity();
        image.setId(31);
        image.setImageUrl(imageUrl);
        when(mapper.findByIds(Collections.singletonList(31))).thenReturn(Collections.singletonList(image));
        when(mapper.deleteTeamPageImageByIds(Collections.singletonList(31))).thenReturn(1);

        assertEquals(1, service.deleteTeamPageImageByIds(Collections.singletonList(31)));

        assertFalse(Files.exists(uploadRoot.resolve(imageUrl)));
    }

    private String stageAndSave(MockMultipartFile file) throws Exception {
        PublicImageUploadInfo upload = service.stageTeamImage(file, UPLOADER_ID);
        Path stagedFile = stagedPath(upload);
        assertTrue(Files.exists(stagedFile));
        assertNoPublicFiles();

        TeamPageImageEntity image = imageFromUpload(upload);
        when(mapper.insertTeamImage(any(TeamPageImageEntity.class))).thenReturn(1);

        assertEquals(1, service.insertTeamImage(image));
        assertFalse(Files.exists(stagedFile));
        return image.getImageUrl();
    }

    private TeamPageImageServiceImpl serviceWithLimits(long maxBytes, int maxStagedFiles) {
        return serviceWithLimits(maxBytes, maxStagedFiles, 10, 0);
    }

    private TeamPageImageServiceImpl serviceWithLimits(
            long maxBytes, int maxStagedFiles, int maxPermanentFiles, long minFreeDiskMegabytes) {
        return serviceWithStorage(
                new FileStorageUtil(uploadRoot.toString(), "test"),
                maxBytes, maxStagedFiles, maxPermanentFiles, minFreeDiskMegabytes);
    }

    private TeamPageImageServiceImpl serviceWithStorage(
            FileStorageUtil storage,
            long maxBytes,
            int maxStagedFiles,
            int maxPermanentFiles,
            long minFreeDiskMegabytes) {
        mapper = mock(TeamPageImageMapper.class);
        quotaMapper = mock(PublicImageQuotaMapper.class);
        fileStorageUtil = storage;
        configureInMemoryPermanentQuota();
        PublicImageValidator validator = new PublicImageValidator(maxBytes, 20_000_000);
        lifecycleService = new PublicImageLifecycleService(
                fileStorageUtil,
                validator,
                quotaMapper,
                1,
                50,
                maxStagedFiles,
                500,
                maxPermanentFiles,
                minFreeDiskMegabytes);
        return new TeamPageImageServiceImpl(mapper, lifecycleService);
    }

    private void configureInMemoryPermanentQuota() {
        permanentFileCount = new AtomicInteger();
        permanentBytes = new AtomicLong();
        permanentAssets = new HashMap<>();
        when(quotaMapper.ensureQuotaRow(anyInt())).thenReturn(1);
        when(quotaMapper.reservePermanentQuota(anyInt(), anyLong(), anyInt(), anyLong()))
                .thenAnswer(invocation -> {
                    long fileSize = invocation.getArgument(1);
                    int maxFiles = invocation.getArgument(2);
                    long maxBytes = invocation.getArgument(3);
                    synchronized (permanentAssets) {
                        if (permanentFileCount.get() >= maxFiles
                                || permanentBytes.get() > maxBytes - fileSize) {
                            return 0;
                        }
                        permanentFileCount.incrementAndGet();
                        permanentBytes.addAndGet(fileSize);
                        return 1;
                    }
                });
        when(quotaMapper.insertAsset(anyString(), anyInt(), anyLong()))
                .thenAnswer(invocation -> {
                    PublicImageAssetEntity asset = new PublicImageAssetEntity();
                    asset.setRelativePath(invocation.getArgument(0));
                    asset.setOwnerUserId(invocation.getArgument(1));
                    asset.setFileSize(invocation.getArgument(2));
                    synchronized (permanentAssets) {
                        return permanentAssets.putIfAbsent(asset.getRelativePath(), asset) == null ? 1 : 0;
                    }
                });
        when(quotaMapper.findAsset(anyString()))
                .thenAnswer(invocation -> permanentAssets.get(invocation.getArgument(0)));
        when(quotaMapper.deleteAsset(anyString()))
                .thenAnswer(invocation -> permanentAssets.remove(invocation.getArgument(0)) == null ? 0 : 1);
        when(quotaMapper.releasePermanentQuota(anyInt(), anyLong()))
                .thenAnswer(invocation -> {
                    permanentFileCount.updateAndGet(value -> Math.max(0, value - 1));
                    permanentBytes.updateAndGet(value -> Math.max(0L, value - (long) invocation.getArgument(1)));
                    return 1;
                });
    }

    private TeamPageImageEntity imageFromUpload(PublicImageUploadInfo upload) {
        TeamPageImageEntity image = new TeamPageImageEntity();
        image.setImageUploadToken(upload.getToken());
        image.setImageUploadUserId(UPLOADER_ID);
        image.setUserId(UPLOADER_ID);
        image.setType(1);
        image.setCaption("caption");
        image.setContent("content");
        return image;
    }

    private Path stagedPath(PublicImageUploadInfo upload) {
        return uploadRoot.resolve("staging/public-images/" + UPLOADER_ID + "/"
                + upload.getToken() + "." + upload.getExtension());
    }

    private long regularFileCount(Path path) throws Exception {
        if (!Files.exists(path)) {
            return 0;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(path)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private void assertNoPublicFiles() throws Exception {
        assertEquals(0, regularFileCount(uploadRoot.resolve("images")));
    }

    private MockMultipartFile file(String name, String contentType, byte[] bytes) {
        return new MockMultipartFile("imageFile", name, contentType, bytes);
    }

    private byte[] imageBytes(String format) throws Exception {
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.BLUE.getRGB());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output));
        return output.toByteArray();
    }

    private byte[] validWebp() {
        return Base64.getDecoder().decode(
                "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
    }

    private void assertNoStoredFiles() throws Exception {
        assertEquals(0, regularFileCount(uploadRoot));
    }
}
