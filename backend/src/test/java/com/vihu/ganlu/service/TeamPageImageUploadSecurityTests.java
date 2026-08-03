package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.PublicImageUploadInfo;
import com.vihu.ganlu.entitys.TeamPageImageEntity;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeamPageImageUploadSecurityTests {
    private static final int UPLOADER_ID = 7;

    @TempDir
    Path uploadRoot;

    private TeamPageImageMapper mapper;
    private FileStorageUtil fileStorageUtil;
    private PublicImageLifecycleService lifecycleService;
    private TeamPageImageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = serviceWithLimits(1024 * 1024, 10);
    }

    @Test
    void acceptsAndStoresDecodedPngWithControlledExtension() throws Exception {
        byte[] png = imageBytes("png");
        String path = stageAndSave(file("team.png", "image/png", png));

        assertTrue(path.matches("images/[0-9a-f-]+\\.png"));
        assertArrayEquals(png, Files.readAllBytes(uploadRoot.resolve(path)));
    }

    @Test
    void acceptsAndStoresDecodedJpegAsJpg() throws Exception {
        byte[] jpeg = imageBytes("jpg");
        String path = stageAndSave(file("team.jpeg", "image/jpeg", jpeg));

        assertTrue(path.matches("images/[0-9a-f-]+\\.jpg"));
        assertArrayEquals(jpeg, Files.readAllBytes(uploadRoot.resolve(path)));
    }

    @Test
    void acceptsAndStoresValidatedWebp() throws Exception {
        byte[] webp = validWebp();
        String path = stageAndSave(file("team.webp", "image/webp", webp));

        assertTrue(path.matches("images/[0-9a-f-]+\\.webp"));
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

        TeamPageImageEntity image = new TeamPageImageEntity();
        image.setImageUploadToken(upload.getToken());
        image.setImageUploadUserId(UPLOADER_ID);
        image.setUserId(UPLOADER_ID);
        image.setType(1);
        image.setCaption("caption");
        image.setContent("content");
        when(mapper.insertTeamImage(any(TeamPageImageEntity.class))).thenReturn(1);

        assertEquals(1, service.insertTeamImage(image));
        assertFalse(Files.exists(stagedFile));
        return image.getImageUrl();
    }

    private TeamPageImageServiceImpl serviceWithLimits(long maxBytes, int maxStagedFiles) {
        mapper = mock(TeamPageImageMapper.class);
        fileStorageUtil = new FileStorageUtil(uploadRoot.toString(), "test");
        PublicImageValidator validator = new PublicImageValidator(maxBytes, 20_000_000);
        lifecycleService = new PublicImageLifecycleService(fileStorageUtil, validator, 1, 50, maxStagedFiles);
        return new TeamPageImageServiceImpl(mapper, lifecycleService);
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
