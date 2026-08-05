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

    // =====================================================================
    // Item 6 exy v4 反向用例：伪造最小 ZIP 必须被拒（堵住旧版"只查条目名"的漏洞）
    // =====================================================================

    @Test
    void validate_emptyContentTypesAndFakeWord_rejected() throws Exception {
        // 旧版漏洞载荷：空 [Content_Types].xml + word/fake → 旧版只查条目名会放行，
        // 新版校验 XML 内容，空 <Types></Types> 无 ContentType 声明 → 拒绝
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("[Content_Types].xml"));
            zos.write("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"></Types>".getBytes("UTF-8"));
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("word/fake"));
            zos.write(new byte[0]);
            zos.closeEntry();
        }
        MockMultipartFile file = new MockMultipartFile("file", "fake.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", baos.toByteArray());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> util.validate(file, FileStorageUtil.MAX_DOCUMENT_SIZE));
        assertTrue(ex.getMessage().contains("OOXML"));
    }

    @Test
    void validate_malformedContentTypesXml_rejected() throws Exception {
        // [Content_Types].xml 非良构 XML → 解析失败 → 拒绝
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("[Content_Types].xml"));
            zos.write("<<<not xml>>>".getBytes("UTF-8"));
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));
            zos.write("<document xmlns=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"/>".getBytes("UTF-8"));
            zos.closeEntry();
        }
        MockMultipartFile file = new MockMultipartFile("file", "bad.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", baos.toByteArray());

        assertThrows(IllegalArgumentException.class,
                () -> util.validate(file, FileStorageUtil.MAX_DOCUMENT_SIZE));
    }

    @Test
    void validate_missingMainPart_rejected() throws Exception {
        // 有合法 [Content_Types].xml（含 ContentType 声明）但缺主部件 word/document.xml → 拒绝
        String contentTypesXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "</Types>";
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("[Content_Types].xml"));
            zos.write(contentTypesXml.getBytes("UTF-8"));
            zos.closeEntry();
            // 故意不放 word/document.xml
        }
        MockMultipartFile file = new MockMultipartFile("file", "no-main.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", baos.toByteArray());

        assertThrows(IllegalArgumentException.class,
                () -> util.validate(file, FileStorageUtil.MAX_DOCUMENT_SIZE));
    }

    // =====================================================================
    // exy v5 Item 6/7 回归
    // =====================================================================

    @Test
    void validate_truncatedMainPart_rejected() throws Exception {
        // Item 6：主部件根标签未闭合（攻击载荷），完整解析应识别为非良构 → 拒绝
        // 旧版 hasRootElement 读到 startElement 即 StopParsingException 终止，未闭合可通过
        String contentTypesXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "</Types>";
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("[Content_Types].xml"));
            zos.write(contentTypesXml.getBytes("UTF-8"));
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));
            // 故意未闭合（无 </document>）——攻击载荷
            zos.write("<?xml version=\"1.0\"?><document xmlns=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><body>".getBytes("UTF-8"));
            zos.closeEntry();
        }
        MockMultipartFile file = new MockMultipartFile("file", "trunc.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", baos.toByteArray());

        assertThrows(IllegalArgumentException.class,
                () -> util.validate(file, FileStorageUtil.MAX_DOCUMENT_SIZE));
    }

    @Test
    void validate_zipBomb_highRatio_rejected() throws Exception {
        // Item 7：单条目高压缩比（解压后超 50MB 单条目上限），LimitedZipInputStream 应终止
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("[Content_Types].xml"));
            // 写一个超大条目（62.5MB 解压后，超过 50MB 单条目上限）
            byte[] chunk = new byte[64 * 1024];
            java.util.Arrays.fill(chunk, (byte) 'x');
            for (int i = 0; i < 1000; i++) { // 1000 × 64KB ≈ 62.5MB 解压
                zos.write(chunk);
            }
            zos.closeEntry();
        }
        MockMultipartFile file = new MockMultipartFile("file", "bomb.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", baos.toByteArray());

        // 高压缩比触发解压上限 → OOXML 校验失败 → 拒绝
        assertThrows(IllegalArgumentException.class,
                () -> util.validate(file, FileStorageUtil.MAX_DOCUMENT_SIZE));
    }

    @Test
    void limitedZipInputStream_perEntryCap_rejectsHugeEntry() throws Exception {
        // Item 7：单条目解压超过单条目上限 → 读取时立即抛 IOException 终止
        // （旧版 closeEntry 会继续排空高压缩比条目；此版本计数后直接终止整个流）
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("big.xml"));
            byte[] chunk = new byte[4096];
            java.util.Arrays.fill(chunk, (byte) 'x');
            for (int i = 0; i < 8; i++) { // 8 × 4KB = 32KB 解压，超过 16KB 单条目上限
                zos.write(chunk);
            }
            zos.closeEntry();
        }
        try (FileStorageUtil.LimitedZipInputStream limited = new FileStorageUtil.LimitedZipInputStream(
                new java.io.ByteArrayInputStream(baos.toByteArray()), 16 * 1024, 1 << 20)) {
            byte[] buf = new byte[4096];
            limited.getNextEntry();
            while (limited.read(buf) != -1) { /* drain */ }
            fail("预期单条目解压超限抛 IOException，实际读完整个条目");
        } catch (java.io.IOException expected) {
            // 预期：单条目解压超过 16KB 上限
        }
    }

    @Test
    void limitedZipInputStream_totalCap_rejectsManyMediumEntries() throws Exception {
        // Item 7：多个中等条目累计解压超过总量上限 → 同样终止
        // （单条目都不超限，只有累计计数能拦截——旧版无累计限制可被分散型 bomb 穿透）
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            byte[] chunk = new byte[4096];
            java.util.Arrays.fill(chunk, (byte) 'x');
            for (int e = 0; e < 6; e++) { // 6 × 4KB = 24KB 解压，超过 16KB 总量上限
                zos.putNextEntry(new java.util.zip.ZipEntry("entry" + e + ".xml"));
                zos.write(chunk);
                zos.closeEntry();
            }
        }
        try (FileStorageUtil.LimitedZipInputStream limited = new FileStorageUtil.LimitedZipInputStream(
                new java.io.ByteArrayInputStream(baos.toByteArray()), 16 * 1024, 16 * 1024)) {
            byte[] buf = new byte[4096];
            java.util.zip.ZipEntry entry;
            while ((entry = limited.getNextEntry()) != null) {
                while (limited.read(buf) != -1) { /* drain */ }
            }
            fail("预期累计解压超限抛 IOException，实际读完 6 个条目");
        } catch (java.io.IOException expected) {
            // 预期：累计解压超过 16KB 总量上限
        }
    }

    @Test
    void validate_largeMainPartDocx_passes() throws Exception {
        // exy v5 Item 6 回归：主部件含填充正文的合法 docx，根元素在 50MB 上限内完整闭合 → 应通过。
        // hasRootElement 完整解析前缀，未闭合会拒；此处 </document> 闭合在 72KB 内，合法通过。
        String contentTypesXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "</Types>";
        StringBuilder mainXml = new StringBuilder(72 * 1024);
        mainXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<document xmlns=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><body>");
        char[] pad = new char[70 * 1024]; // 填充正文，主部件 72KB（在 50MB 上限内）
        java.util.Arrays.fill(pad, 'x');
        mainXml.append(pad).append("</body></document>");

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("[Content_Types].xml"));
            zos.write(contentTypesXml.getBytes("UTF-8"));
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));
            zos.write(mainXml.toString().getBytes("UTF-8"));
            zos.closeEntry();
        }
        MockMultipartFile file = new MockMultipartFile("file", "large.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", baos.toByteArray());

        FileStorageUtil.ValidatedFile vf = util.validate(file, FileStorageUtil.MAX_DOCUMENT_SIZE);
        assertEquals(FileStorageUtil.FileCategory.DOCUMENT, vf.getCategory());
        assertEquals("docx", vf.getExtension());
    }

    @Test
    void validate_relsPresentButInvalidTarget_rejected() throws Exception {
        // exy v5 Item 6 回归：_rels/.rels 存在但 officeDocument 关系指向错误主部件 → 拒绝。
        // 覆盖 isRealOoxml 第 472-474 行分支：ContentTypes+主部件均合法，仅因 .rels 不一致而拒。
        String contentTypesXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "</Types>";
        String mainXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<document xmlns=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><body/></document>";
        // .rels 存在但 officeDocument 关系 Target 指向 word/other.xml（非主部件 word/document.xml）
        String relsXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/other.xml\"/>"
                + "</Relationships>";

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("[Content_Types].xml"));
            zos.write(contentTypesXml.getBytes("UTF-8"));
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));
            zos.write(mainXml.getBytes("UTF-8"));
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("_rels/.rels"));
            zos.write(relsXml.getBytes("UTF-8"));
            zos.closeEntry();
        }
        MockMultipartFile file = new MockMultipartFile("file", "bad-rels.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", baos.toByteArray());

        assertThrows(IllegalArgumentException.class,
                () -> util.validate(file, FileStorageUtil.MAX_DOCUMENT_SIZE));
    }

    @Test
    void validate_relsMissingButValid_passes() throws Exception {
        // exy v5 Item 6：_rels/.rels 缺失但 ContentTypes+主部件均合法 → 放行。
        // 锁定".rels 可选"语义，防止未来误改为强制要求 .rels 才通过。
        String contentTypesXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "</Types>";
        String mainXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<document xmlns=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><body/></document>";

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("[Content_Types].xml"));
            zos.write(contentTypesXml.getBytes("UTF-8"));
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));
            zos.write(mainXml.getBytes("UTF-8"));
            zos.closeEntry();
            // 故意不放 _rels/.rels
        }
        MockMultipartFile file = new MockMultipartFile("file", "no-rels.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", baos.toByteArray());

        FileStorageUtil.ValidatedFile vf = util.validate(file, FileStorageUtil.MAX_DOCUMENT_SIZE);
        assertEquals(FileStorageUtil.FileCategory.DOCUMENT, vf.getCategory());
        assertEquals("docx", vf.getExtension());
    }

    /** 构造合法 OOXML ZIP 字节：含符合 schema 的 [Content_Types].xml 和主部件。
     *  prefix = "word/"（docx）或 "ppt/"（pptx），对应 mainPart/ContentType/命名空间不同。 */
    private byte[] buildOoxmlZip(String prefix) throws Exception {
        boolean isDocx = "word/".equals(prefix);
        String mainPart = isDocx ? "word/document.xml" : "ppt/presentation.xml";
        String partName = isDocx ? "/word/document.xml" : "/ppt/presentation.xml";
        String mainContentType = isDocx
                ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"
                : "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml";
        String mainNs = isDocx
                ? "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                : "http://schemas.openxmlformats.org/presentationml/2006/main";
        String mainRoot = isDocx ? "document" : "presentation";

        String contentTypesXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Types xmlns=\"" + "http://schemas.openxmlformats.org/package/2006/content-types" + "\">"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Override PartName=\"" + partName + "\" ContentType=\"" + mainContentType + "\"/>"
                + "</Types>";
        String mainXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<" + mainRoot + " xmlns=\"" + mainNs + "\"><body/></" + mainRoot + ">";

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("[Content_Types].xml"));
            zos.write(contentTypesXml.getBytes("UTF-8"));
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry(mainPart));
            zos.write(mainXml.getBytes("UTF-8"));
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
