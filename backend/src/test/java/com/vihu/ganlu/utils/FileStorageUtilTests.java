package com.vihu.ganlu.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileStorageUtil 文件校验单元测试。
 */
class FileStorageUtilTests {
    private FileStorageUtil util;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        util = new FileStorageUtil(tempDir.toString(), "test");
    }

    @Test
    void validate_validJpeg_passes() {
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpeg);

        FileStorageUtil.ValidatedFile vf = util.validate(file, FileStorageUtil.MAX_IMAGE_SIZE);
        assertEquals(FileStorageUtil.FileCategory.IMAGE, vf.getCategory());
        assertEquals("jpg", vf.getExtension());
    }

    @Test
    void validate_validPng_passes() {
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", png);

        FileStorageUtil.ValidatedFile vf = util.validate(file, FileStorageUtil.MAX_IMAGE_SIZE);
        assertEquals(FileStorageUtil.FileCategory.IMAGE, vf.getCategory());
        assertEquals("png", vf.getExtension());
    }

    @Test
    void validate_validPdf_passes() {
        byte[] pdf = "%PDF-1.4 rest of content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", pdf);

        FileStorageUtil.ValidatedFile vf = util.validate(file, FileStorageUtil.MAX_DOCUMENT_SIZE);
        assertEquals(FileStorageUtil.FileCategory.DOCUMENT, vf.getCategory());
        assertEquals("pdf", vf.getExtension());
    }

    @Test
    void validate_exeDisguisedAsJpg_rejected() {
        // .exe 魔数（MZ 头）伪装成 .jpg
        byte[] exe = new byte[]{0x4D, 0x5A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "malware.jpg", "image/jpeg", exe);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> util.validate(file, FileStorageUtil.MAX_IMAGE_SIZE));
        assertTrue(ex.getMessage().contains("魔数"));
    }

    @Test
    void validate_unsupportedExtension_rejected() {
        byte[] data = "hello".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "file.xyz", "application/octet-stream", data);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> util.validate(file, FileStorageUtil.MAX_IMAGE_SIZE));
        assertTrue(ex.getMessage().contains("不支持"));
    }

    @Test
    void validate_exceedsMaxSize_rejected() {
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", jpeg);

        // 限制 1 byte — 文件超过限制
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> util.validate(file, 1));
        assertTrue(ex.getMessage().contains("大小"));
    }

    @Test
    void validate_emptyFile_rejected() {
        MockMultipartFile file = new MockMultipartFile("file", new byte[0]);
        assertThrows(IllegalArgumentException.class,
                () -> util.validate(file, FileStorageUtil.MAX_IMAGE_SIZE));
    }

    @Test
    void isAllowedImage_validJpeg_passes() {
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpeg);

        FileStorageUtil.ValidatedFile vf = util.isAllowedImage(file);
        assertEquals(FileStorageUtil.FileCategory.IMAGE, vf.getCategory());
    }

    @Test
    void storeFile_and_loadFile_and_deleteFile() throws IOException {
        byte[] content = "hello world".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", content);

        String relativePath = util.storeFile(file, "docs");
        assertNotNull(relativePath);

        Path loaded = util.loadFile(relativePath);
        assertTrue(Files.exists(loaded));

        boolean deleted = util.deleteFile(relativePath);
        assertTrue(deleted);
        assertFalse(Files.exists(loaded));
    }

    @Test
    void deleteFile_nonExistent_returnsFalse() {
        boolean result = util.deleteFile("non/existent.txt");
        assertFalse(result);
    }

    @Test
    void loadFile_pathTraversal_rejected() {
        // 试图通过 ../ 逃出 uploadRoot，应被 loadFile 拒绝
        assertThrows(FileStorageUtil.StorageException.class,
                () -> util.loadFile("../../etc/passwd"));
    }

    @Test
    void storeFile_pathTraversalInOriginalName_sanitized() throws IOException {
        // 原始文件名包含 ../ ，storeFile 应忽略原始文件名，只用 UUID + 验证扩展名
        byte[] content = "data".getBytes();
        MockMultipartFile file = new MockMultipartFile("file",
                "../../../../outside.txt", "text/plain", content);

        String relativePath = util.storeFile(file, "docs");
        Path stored = util.loadFile(relativePath); // 不抛异常即说明在 uploadRoot 内
        assertTrue(Files.exists(stored));
        // 存储名不应包含原始的越界片段
        assertFalse(relativePath.contains(".."));
        assertFalse(relativePath.contains("outside"));
    }

    @Test
    void moveFile_movesBetweenSubdirs() throws IOException {
        // Item 4: 文件应能从 images_pending/ move 到 images/
        byte[] content = "img-data".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", content);
        String fromPath = util.storeFile(file, "images_pending");
        assertTrue(fromPath.startsWith("images_pending/"));

        String filename = fromPath.substring(fromPath.indexOf('/') + 1);
        String toPath = "images/" + filename;

        Path moved = util.moveFile(fromPath, toPath);
        assertTrue(Files.exists(moved));
        assertTrue(moved.toString().replace("\\", "/").contains("images/"));
        // 源文件应已不存在
        assertFalse(Files.exists(util.loadFile(fromPath)));
    }

    @Test
    void moveFile_pathTraversal_rejected() throws IOException {
        // 目标路径含 ../ 试图逃出 uploadRoot，应被拒绝
        byte[] content = "x".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", content);
        String fromPath = util.storeFile(file, "images_pending");

        assertThrows(FileStorageUtil.StorageException.class,
                () -> util.moveFile(fromPath, "../../etc/evil.jpg"));
    }

    @Test
    void moveFile_targetExists_rejected() throws IOException {
        // 目标文件已存在时 move 应失败，避免覆盖
        byte[] content = "x".getBytes();
        MockMultipartFile f1 = new MockMultipartFile("file", "a.jpg", "image/jpeg", content);
        String fromPath = util.storeFile(f1, "images_pending");
        MockMultipartFile f2 = new MockMultipartFile("file", "b.jpg", "image/jpeg", content);
        String existingPath = util.storeFile(f2, "images");

        String filename = fromPath.substring(fromPath.indexOf('/') + 1);
        // 已存在的同名文件（用 storeFile 保证文件名不同；这里构造目标为已存在的 existingPath）
        assertThrows(FileStorageUtil.StorageException.class,
                () -> util.moveFile(fromPath, existingPath));
    }

    @Test
    void validate_plaintextRenamedAsDocx_rejected() {
        // 纯文本内容改名为 .docx，应被 ZIP 魔数校验拒绝
        byte[] plaintext = "this is just plain text, not a real docx".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "fake.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", plaintext);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> util.validate(file, FileStorageUtil.MAX_DOCUMENT_SIZE));
        assertTrue(ex.getMessage().contains("魔数"));
    }

    @Test
    void validate_validZipAsDocx_passes() {
        // 真正的 ZIP（docx/pptx 本质）以 PK\x03\x04 开头，应通过
        byte[] zipHeader = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "archive.zip",
                "application/zip", zipHeader);

        FileStorageUtil.ValidatedFile vf = util.validate(file, FileStorageUtil.MAX_DOCUMENT_SIZE);
        assertEquals(FileStorageUtil.FileCategory.DOCUMENT, vf.getCategory());
    }

    // =====================================================================
    // Item 9: docx/pptx OOXML 真实格式校验
    // 普通 ZIP 改名为 docx/pptx 应被拒绝，真正的 OOXML 通过
    // =====================================================================

    @Test
    void validate_realDocx_passes() throws Exception {
        // 构造包含 [Content_Types].xml 和 word/document.xml 的合法 OOXML ZIP
        byte[] ooxml = buildOoxmlZip("word/");
        MockMultipartFile file = new MockMultipartFile("file", "real.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ooxml);

        FileStorageUtil.ValidatedFile vf = util.validate(file, FileStorageUtil.MAX_DOCUMENT_SIZE);
        assertEquals(FileStorageUtil.FileCategory.DOCUMENT, vf.getCategory());
        assertEquals("docx", vf.getExtension());
    }

    @Test
    void validate_realPptx_passes() throws Exception {
        byte[] ooxml = buildOoxmlZip("ppt/");
        MockMultipartFile file = new MockMultipartFile("file", "real.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation", ooxml);

        FileStorageUtil.ValidatedFile vf = util.validate(file, FileStorageUtil.MAX_DOCUMENT_SIZE);
        assertEquals(FileStorageUtil.FileCategory.DOCUMENT, vf.getCategory());
        assertEquals("pptx", vf.getExtension());
    }

    @Test
    void validate_plainZipRenamedAsDocx_rejected() throws Exception {
        // 普通 ZIP（无 [Content_Types].xml 和 word/）改名为 docx → 应拒绝
        byte[] plainZip = buildPlainZip();
        MockMultipartFile file = new MockMultipartFile("file", "fake.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", plainZip);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> util.validate(file, FileStorageUtil.MAX_DOCUMENT_SIZE));
        assertTrue(ex.getMessage().contains("OOXML"));
    }

    @Test
    void validate_plainZipAsZip_stillPasses() throws Exception {
        // 普通 zip 文件（非 docx/pptx）不进入 OOXML 校验，仅验 ZIP 头即可
        byte[] plainZip = buildPlainZip();
        MockMultipartFile file = new MockMultipartFile("file", "archive.zip",
                "application/zip", plainZip);

        FileStorageUtil.ValidatedFile vf = util.validate(file, FileStorageUtil.MAX_DOCUMENT_SIZE);
        assertEquals(FileStorageUtil.FileCategory.DOCUMENT, vf.getCategory());
        assertEquals("zip", vf.getExtension());
    }

    /** 构造合法 OOXML ZIP 字节：含 [Content_Types].xml 和 <prefix> 条目 */
    private byte[] buildOoxmlZip(String prefix) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("[Content_Types].xml"));
            zos.write("<Types/>".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry(prefix + "document.xml"));
            zos.write("<doc/>".getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    /** 构造普通 ZIP（无 OOXML 标志条目） */
    private byte[] buildPlainZip() throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("readme.txt"));
            zos.write("hello".getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
