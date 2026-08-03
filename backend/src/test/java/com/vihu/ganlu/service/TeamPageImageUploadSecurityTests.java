package com.vihu.ganlu.service;

import com.vihu.ganlu.mappers.TeamPageImageMapper;
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
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TeamPageImageUploadSecurityTests {
    @TempDir
    Path uploadRoot;

    private TeamPageImageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = serviceWithLimit(1024 * 1024);
    }

    @Test
    void acceptsAndStoresDecodedPngWithControlledExtension() throws Exception {
        byte[] png = imageBytes("png");
        String path = service.uploadTeamImage(file("team.png", "image/png", png));

        assertTrue(path.matches("images/[0-9a-f-]+\\.png"));
        assertArrayEquals(png, Files.readAllBytes(uploadRoot.resolve(path)));
    }

    @Test
    void acceptsAndStoresDecodedJpegAsJpg() throws Exception {
        byte[] jpeg = imageBytes("jpg");
        String path = service.uploadTeamImage(file("team.jpeg", "image/jpeg", jpeg));

        assertTrue(path.matches("images/[0-9a-f-]+\\.jpg"));
        assertArrayEquals(jpeg, Files.readAllBytes(uploadRoot.resolve(path)));
    }

    @Test
    void acceptsAndStoresValidatedWebp() throws Exception {
        byte[] webp = validWebp();
        String path = service.uploadTeamImage(file("team.webp", "image/webp", webp));

        assertTrue(path.matches("images/[0-9a-f-]+\\.webp"));
        assertArrayEquals(webp, Files.readAllBytes(uploadRoot.resolve(path)));
    }

    @Test
    void rejectsHtmlAndLeavesNoPublicFile() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.uploadTeamImage(
                file("attack.html", "text/html", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8))));
        assertNoStoredFiles();
    }

    @Test
    void rejectsSvgAndLeavesNoPublicFile() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.uploadTeamImage(
                file("attack.svg", "image/svg+xml", "<svg onload='alert(1)'></svg>".getBytes(StandardCharsets.UTF_8))));
        assertNoStoredFiles();
    }

    @Test
    void rejectsTextDisguisedAsJpegAndLeavesNoPublicFile() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.uploadTeamImage(
                file("fake.jpg", "image/jpeg", "not a jpeg".getBytes(StandardCharsets.UTF_8))));
        assertNoStoredFiles();
    }

    @Test
    void rejectsWebpWithTrailingDataAndLeavesNoPublicFile() throws Exception {
        byte[] webp = validWebp();
        byte[] withTrailingData = Arrays.copyOf(webp, webp.length + 1);

        assertThrows(IllegalArgumentException.class, () -> service.uploadTeamImage(
                file("invalid.webp", "image/webp", withTrailingData)));
        assertNoStoredFiles();
    }

    @Test
    void rejectsFileOverConfiguredLimitAndLeavesNoPublicFile() throws Exception {
        TeamPageImageServiceImpl limitedService = serviceWithLimit(32);
        assertThrows(IllegalArgumentException.class, () -> limitedService.uploadTeamImage(
                file("large.png", "image/png", imageBytes("png"))));
        assertNoStoredFiles();
    }

    private TeamPageImageServiceImpl serviceWithLimit(long maxBytes) {
        return new TeamPageImageServiceImpl(
                mock(TeamPageImageMapper.class),
                new FileStorageUtil(uploadRoot.toString(), "test"),
                new PublicImageValidator(maxBytes, 20_000_000));
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
        try (java.util.stream.Stream<Path> paths = Files.walk(uploadRoot)) {
            assertEquals(0, paths.filter(Files::isRegularFile).count());
        }
    }
}
