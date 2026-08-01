package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.UploadedFileInfo;
import com.vihu.ganlu.service.impl.MaterialUploadStorageService;
import com.vihu.ganlu.utils.FileStorageUtil;
import com.vihu.ganlu.utils.MaterialFileValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialUploadStorageServiceTests {
    @TempDir
    Path uploadRoot;

    private MaterialUploadStorageService storageService;

    @BeforeEach
    void setUp() {
        FileStorageUtil fileStorage = new FileStorageUtil(uploadRoot.toString(), "test");
        storageService = new MaterialUploadStorageService(
                fileStorage, new MaterialFileValidator(), 1, 1, 1024, 5);
    }

    @Test
    void rejectsChunkWhoseBytesExceedDeclaredFileSize() {
        MockMultipartFile oversized = new MockMultipartFile("file", "tiny.pdf",
                "application/octet-stream", new byte[]{1, 2});

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> storageService.saveChunk(oversized, 1, 1,
                        "c4ca4238a0b923820dcc509a6f75849b", "tiny.pdf", 1, "MATERIAL", 7));

        assertTrue(error.getMessage().contains("分片大小"));
    }

    @Test
    void resumesChunksButRejectsChangedSessionParameters() throws Exception {
        byte[] content = "abc".getBytes(StandardCharsets.US_ASCII);
        String checksum = md5(content);
        MockMultipartFile chunk = new MockMultipartFile("file", "lesson.pdf",
                "application/octet-stream", content);

        storageService.saveChunk(chunk, 1, 1, checksum, "lesson.pdf", content.length, "MATERIAL", 8);
        Map<String, Object> state = storageService.checkFileExist(checksum, "MATERIAL", 8);

        assertFalse((Boolean) state.get("complete"));
        assertEquals(java.util.Collections.singletonList(1), state.get("uploadedChunks"));
        assertThrows(IllegalArgumentException.class,
                () -> storageService.saveChunk(chunk, 1, 1, checksum,
                        "changed.pdf", content.length, "MATERIAL", 8));
    }

    @Test
    void cancellingMergedUploadRemovesStagedFileAndChecksumIndex() throws Exception {
        byte[] content = "%PDF-1.7\nmaterial".getBytes(StandardCharsets.US_ASCII);
        String checksum = md5(content);
        MockMultipartFile chunk = new MockMultipartFile("file", "lesson.pdf",
                "application/pdf", content);

        storageService.saveChunk(chunk, 1, 1, checksum, "lesson.pdf", content.length, "MATERIAL", 9);
        UploadedFileInfo staged = storageService.mergeChunks(
                "lesson.pdf", checksum, 1, content.length, "MATERIAL", 9);
        assertTrue((Boolean) storageService.checkFileExist(checksum, "MATERIAL", 9).get("complete"));

        storageService.cancelUpload(9, "MATERIAL", checksum, staged.getToken());

        Map<String, Object> state = storageService.checkFileExist(checksum, "MATERIAL", 9);
        assertFalse((Boolean) state.get("complete"));
        assertEquals(java.util.Collections.emptyList(), state.get("uploadedChunks"));
        try (java.util.stream.Stream<Path> paths = Files.walk(uploadRoot.resolve("staging/materials/9"))) {
            List<Path> stagedArtifacts = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals(".user.lock"))
                    .collect(java.util.stream.Collectors.toList());
            assertTrue(stagedArtifacts.isEmpty());
        }
    }

    @Test
    void cleanupRemovesInterruptedUploadAfterTtl() throws Exception {
        byte[] content = "abc".getBytes(StandardCharsets.US_ASCII);
        String checksum = md5(content);
        MockMultipartFile chunk = new MockMultipartFile("file", "lesson.pdf",
                "application/octet-stream", content);
        storageService.saveChunk(chunk, 1, 1, checksum, "lesson.pdf", content.length, "MATERIAL", 10);
        Path sessionMetadata = uploadRoot.resolve(
                "temp_chunks/10/material/" + checksum + "/session.properties");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(sessionMetadata)) {
            properties.load(input);
        }
        properties.setProperty("lastActivity", "0");
        try (OutputStream output = Files.newOutputStream(sessionMetadata)) {
            properties.store(output, "expired test session");
        }

        storageService.cleanupExpiredUploads();

        assertFalse(Files.exists(sessionMetadata.getParent()));
    }

    private String md5(byte[] content) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5").digest(content);
        StringBuilder checksum = new StringBuilder();
        for (byte value : digest) {
            checksum.append(String.format("%02x", value & 0xff));
        }
        return checksum.toString();
    }
}
