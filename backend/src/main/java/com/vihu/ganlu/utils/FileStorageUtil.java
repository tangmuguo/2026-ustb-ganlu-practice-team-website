package com.vihu.ganlu.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

@Slf4j
@Component
public class FileStorageUtil {
    private final Path uploadRoot;
    private final String activeProfile;

    public static final List<String> IMAGE_EXT = Arrays.asList("jpg", "jpeg", "png", "webp");
    public static final List<String> VIDEO_EXT = Arrays.asList("mp4", "mov");
    public static final List<String> DOC_EXT = Arrays.asList("pdf", "doc", "docx", "ppt", "pptx", "zip");
    public static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024;
    public static final long MAX_VIDEO_SIZE = 200 * 1024 * 1024;
    public static final long MAX_DOCUMENT_SIZE = 200 * 1024 * 1024;

    private static final byte[] MAGIC_JPEG = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] MAGIC_PNG = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] MAGIC_WEBP_RIFF = "RIFF".getBytes();
    private static final byte[] MAGIC_WEBP_WEBP = "WEBP".getBytes();
    private static final byte[] MAGIC_MP4_FTYP = "ftyp".getBytes();
    private static final byte[] MAGIC_PDF = "%PDF".getBytes();
    private static final byte[] MAGIC_ZIP = new byte[]{0x50, 0x4B, 0x03, 0x04};
    private static final byte[] MAGIC_OLE = new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final int MAX_OOXML_ENTRIES = 1000;

    public FileStorageUtil(
            @Value("${file.upload-dir}") String uploadDir,
            @Value("${spring.profiles.active:${spring.profiles.default:prod}}") String activeProfile) {
        this.activeProfile = activeProfile;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(uploadRoot);
            log.info("当前运行环境: {}", this.activeProfile);
            log.info("文件上传目录已初始化: {}", uploadRoot);
            if ("prod".equals(this.activeProfile)) {
                checkDirectoryPermissions();
            }
        } catch (IOException e) {
            throw new StorageException("无法创建上传目录: " + uploadRoot, e);
        }
    }

    public Path getUploadRoot() {
        return uploadRoot;
    }

    public long getUsableSpace() {
        return getUsableSpace(uploadRoot);
    }

    public long getUsableSpace(Path directory) {
        try {
            return Files.getFileStore(directory.toAbsolutePath().normalize()).getUsableSpace();
        } catch (IOException e) {
            throw new StorageException("无法读取磁盘剩余空间: " + directory, e);
        }
    }

    public Path createDirectory(String relativeDirectory) {
        Path directory = resolveSafe(relativeDirectory);
        try {
            Files.createDirectories(directory);
            return directory;
        } catch (IOException e) {
            throw new StorageException("无法创建目录: " + relativeDirectory, e);
        }
    }

    public String storeFile(MultipartFile file, String subDir) {
        String originalName = safeLeafName(file == null ? null : file.getOriginalFilename());
        return storeFile(file, subDir, extensionOf(originalName));
    }

    public String storeFile(MultipartFile file, String subDir, String forcedExtension) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String originalName = safeLeafName(file.getOriginalFilename());
        String extension = sanitizeExtension(forcedExtension);
        String targetName = UUID.randomUUID().toString()
                + (extension.isEmpty() ? "" : "." + extension);
        Path target = createDirectory(subDir).resolve(targetName).normalize();
        ensureInsideRoot(target);

        Path staging = null;
        try {
            // Staging lives outside /images/**, so a failed or partial copy is never same-origin public.
            staging = Files.createTempFile(uploadRoot, ".upload-", ".tmp");
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, staging, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(staging, target);
            }
            return toRelativePath(target);
        } catch (IOException e) {
            deleteQuietly(staging);
            deleteQuietly(target);
            throw new StorageException("存储文件失败: " + originalName, e);
        } finally {
            deleteQuietly(staging);
        }
    }

    public String moveInto(Path source, String subDir, String extension) {
        ensureExistingSource(source);
        String safeExtension = sanitizeExtension(extension);
        Path target = createDirectory(subDir).resolve(
                UUID.randomUUID().toString() + (safeExtension.isEmpty() ? "" : "." + safeExtension)
        ).normalize();
        ensureInsideRoot(target);
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            return toRelativePath(target);
        } catch (IOException e) {
            throw new StorageException("移动文件失败", e);
        }
    }

    public String copyInto(Path source, String subDir, String extension) {
        ensureExistingSource(source);
        String safeExtension = sanitizeExtension(extension);
        Path target = createDirectory(subDir).resolve(
                UUID.randomUUID().toString() + (safeExtension.isEmpty() ? "" : "." + safeExtension)
        ).normalize();
        ensureInsideRoot(target);
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return toRelativePath(target);
        } catch (IOException e) {
            throw new StorageException("复制文件失败", e);
        }
    }

    /** Allocates a collision-resistant path without creating the file, so recovery intent can be persisted first. */
    public String allocatePath(String subDir, String extension) {
        String safeExtension = sanitizeExtension(extension);
        Path target = createDirectory(subDir).resolve(
                UUID.randomUUID().toString() + (safeExtension.isEmpty() ? "" : "." + safeExtension)
        ).normalize();
        ensureInsideRoot(target);
        if (Files.exists(target)) {
            throw new StorageException("目标文件已存在: " + toRelativePath(target));
        }
        return toRelativePath(target);
    }

    /** Copies into a previously allocated path. The staged source remains available until DB commit succeeds. */
    public void copyToAllocatedPath(Path source, String relativePath) {
        ensureExistingSource(source);
        Path target = loadFile(relativePath);
        if (Files.exists(target)) {
            throw new StorageException("目标文件已存在: " + relativePath);
        }
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target);
        } catch (IOException error) {
            deleteQuietly(target);
            throw new StorageException("复制文件失败: " + relativePath, error);
        }
    }

    public Path moveFile(String fromRelativePath, String toRelativePath) {
        Path source = loadFile(fromRelativePath);
        Path target = loadFile(toRelativePath);
        if (!Files.isRegularFile(source)) {
            throw new StorageException("源文件不存在: " + fromRelativePath);
        }
        if (Files.exists(target)) {
            throw new StorageException("目标文件已存在: " + toRelativePath);
        }
        try {
            Files.createDirectories(target.getParent());
            try {
                return Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                return Files.move(source, target);
            }
        } catch (IOException e) {
            throw new StorageException("移动文件失败: " + fromRelativePath + " -> " + toRelativePath, e);
        }
    }

    public Path loadFile(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            throw new IllegalArgumentException("文件路径不能为空");
        }
        // 兼容数据库中 Windows 历史数据残留的反斜杠路径。
        String normalizedPath = relativePath.replace('\\', '/');
        Path resolved = uploadRoot.resolve(normalizedPath).toAbsolutePath().normalize();
        ensureInsideRoot(resolved);
        return resolved;
    }

    public boolean deleteFile(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return false;
        }
        Path filePath = loadFile(relativePath);
        try {
            if (!Files.exists(filePath)) {
                log.warn("文件不存在，跳过删除: {}", filePath);
                return false;
            }
            Files.delete(filePath);
            return true;
        } catch (IOException e) {
            throw new StorageException("删除文件失败: " + relativePath, e);
        }
    }

    public void deleteTree(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        Path normalized = directory.toAbsolutePath().normalize();
        ensureInsideRoot(normalized);
        if (normalized.equals(uploadRoot)) {
            throw new SecurityException("禁止删除上传根目录");
        }
        try (Stream<Path> paths = Files.walk(normalized)) {
            paths
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new StorageException("清理临时文件失败: " + path, e);
                        }
                    });
        } catch (IOException e) {
            throw new StorageException("清理临时目录失败: " + normalized, e);
        }
    }

    public String toRelativePath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        ensureInsideRoot(normalized);
        return uploadRoot.relativize(normalized).toString().replace('\\', '/');
    }

    public static String safeLeafName(String originalName) {
        String cleaned = StringUtils.cleanPath(originalName == null ? "file" : originalName)
                .replace('\\', '/');
        String leaf = cleaned.substring(cleaned.lastIndexOf('/') + 1).trim();
        if (!StringUtils.hasText(leaf) || ".".equals(leaf) || "..".equals(leaf)) {
            return "file";
        }
        return leaf.replaceAll("[\\r\\n\\t]", "_");
    }

    public static String extensionOf(String filename) {
        String leaf = safeLeafName(filename);
        int dot = leaf.lastIndexOf('.');
        return dot < 0 ? "" : sanitizeExtension(leaf.substring(dot + 1));
    }

    public String extractExtension(String filename) {
        return extensionOf(filename);
    }

    public enum FileCategory {
        IMAGE, VIDEO, DOCUMENT
    }

    public static class ValidatedFile {
        private FileCategory category;
        private String extension;
        private String mimeType;
        private long size;
        private MultipartFile raw;

        public FileCategory getCategory() { return category; }
        public void setCategory(FileCategory category) { this.category = category; }
        public String getExtension() { return extension; }
        public void setExtension(String extension) { this.extension = extension; }
        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }
        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }
        public MultipartFile getRaw() { return raw; }
        public void setRaw(MultipartFile raw) { this.raw = raw; }
    }

    public ValidatedFile validate(MultipartFile file, long maxSizeBytes) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("文件为空");
        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) throw new IllegalArgumentException("文件名为空");
        if (file.getSize() > maxSizeBytes) throw new IllegalArgumentException("文件大小超过限制");

        String extension = extractExtension(originalFilename);
        FileCategory category;
        if (IMAGE_EXT.contains(extension)) category = FileCategory.IMAGE;
        else if (VIDEO_EXT.contains(extension)) category = FileCategory.VIDEO;
        else if (DOC_EXT.contains(extension)) category = FileCategory.DOCUMENT;
        else throw new IllegalArgumentException("不支持的文件类型: " + extension);

        byte[] header = new byte[12];
        try (InputStream input = file.getInputStream()) {
            int length = input.read(header);
            if (length < 8) throw new IllegalArgumentException("文件头过短，无法校验");
        } catch (IOException error) {
            throw new IllegalArgumentException("读取文件失败", error);
        }
        if (!matchesMagic(header, category, extension)) {
            throw new IllegalArgumentException("文件内容与实际扩展名不符（魔数校验失败）");
        }
        if (category == FileCategory.DOCUMENT
                && ("docx".equals(extension) || "pptx".equals(extension))
                && !isRealOoxml(file, extension)) {
            throw new IllegalArgumentException("文件不是有效的 " + extension + "（OOXML 结构校验失败）");
        }

        ValidatedFile validated = new ValidatedFile();
        validated.setCategory(category);
        validated.setExtension(extension);
        validated.setMimeType(file.getContentType());
        validated.setSize(file.getSize());
        validated.setRaw(file);
        return validated;
    }

    public ValidatedFile isAllowedImage(MultipartFile file) {
        ValidatedFile validated = validate(file, MAX_IMAGE_SIZE);
        if (validated.getCategory() != FileCategory.IMAGE) {
            throw new IllegalArgumentException("仅支持图片文件");
        }
        return validated;
    }

    public ValidatedFile isAllowedVideo(MultipartFile file) {
        ValidatedFile validated = validate(file, MAX_VIDEO_SIZE);
        if (validated.getCategory() != FileCategory.VIDEO) {
            throw new IllegalArgumentException("仅支持视频文件");
        }
        return validated;
    }

    public ValidatedFile isAllowedDocument(MultipartFile file) {
        ValidatedFile validated = validate(file, MAX_DOCUMENT_SIZE);
        if (validated.getCategory() != FileCategory.DOCUMENT) {
            throw new IllegalArgumentException("仅支持文档文件");
        }
        return validated;
    }

    private boolean matchesMagic(byte[] header, FileCategory category, String extension) {
        switch (category) {
            case IMAGE:
                if ("jpg".equals(extension) || "jpeg".equals(extension)) return startsWith(header, MAGIC_JPEG);
                if ("png".equals(extension)) return startsWith(header, MAGIC_PNG);
                return "webp".equals(extension)
                        && startsWith(header, MAGIC_WEBP_RIFF)
                        && matchBytes(header, 8, MAGIC_WEBP_WEBP);
            case VIDEO:
                return matchBytes(header, 4, MAGIC_MP4_FTYP);
            case DOCUMENT:
                if ("pdf".equals(extension)) return startsWith(header, MAGIC_PDF);
                if ("docx".equals(extension) || "pptx".equals(extension) || "zip".equals(extension)) {
                    return startsWith(header, MAGIC_ZIP);
                }
                return startsWith(header, MAGIC_OLE);
            default:
                return false;
        }
    }

    /**
     * OOXML 真实格式校验（Item 6 exy v4）。
     * docx/pptx 本质是 ZIP，仅凭 PK 头无法区分。旧版只检查条目名是否存在，攻击者构造
     * 只含空 [Content_Types].xml + word/fake 的 ZIP 改名 docx 即可绕过。
     *
     * 加强点：
     *   - 解析关键条目的 XML 内容（非仅条目名），校验 [Content_Types].xml 的 ContentType 声明
     *     与主部件（word/document.xml 或 ppt/presentation.xml）的根元素命名空间。
     *   - 完整 XXE 防护（解析的是攻击者可控上传 XML，经典 XXE 面）。
     *   - 跨条目扫描：两个目标条目可能以任意顺序出现，扫到都校验通过才放行（不命中即 return）。
     *   - 主部件非空用实际读出字节判断（不信 ZipEntry.getSize()，流式 ZIP 该值返回 -1）。
     *   - 限制最多扫 MAX_OOXML_ENTRIES 个条目防 zip bomb；单条目读取上限 MAX_OOXML_PART_BYTES。
     *   - 保留 !name.contains("..") 守卫（只读内存无 Zip Slip 风险，但守卫已有，不删）。
     * 普通 zip 不走此方法（继续只验 ZIP 容器）。
     */
    // exy v5 Item 6：hasRootElement 流式完整解析（未闭合即拒），前缀上限决定 document.xml
    // 超过多大时会被误拒（前缀截断 → SAX 报未闭合）。50MB 覆盖几乎所有合法 docx。
    // 代价：校验时每文件最多读 50MB 前缀到内存（流式解析不建 DOM，内存≈输入字节）。
    private static final int MAX_OOXML_PART_BYTES = 50 * 1024 * 1024; // 主部件最多读 50MB
    // exy v6 P2#5：元数据部件（[Content_Types].xml / _rels/.rels）专用小上限。
    // 这两个部件正常极小（几 KB），无需借用主部件的 50MB 上限；它们读入 byte[] 后还经
    // parseSecureXml 建 DOM（DOM 对象常把原始 XML 放大数倍），几个并发上传即可造成远高于
    // 50MB/请求的堆占用。1MB 已远超任何合法 OOXML 包级元数据大小，收紧后消除堆压力。
    private static final int MAX_OOXML_METADATA_PART_BYTES = 1 * 1024 * 1024; // 元数据部件最多读 1MB
    // exy v5 Item 7：zip bomb 防护——限制每条目解压后字节数 + 全部条目累计解压字节数。
    // 单条目上限对嵌入媒体同样生效：含单张 >50MB 图片的文档会被拒（与 document.xml 50MB 边界同源）。
    private static final int MAX_OOXML_INFLATED_PART_BYTES = 50 * 1024 * 1024; // 单条目解压上限 50MB
    // 累计上限取 2× 文件大小上限（400MB）：媒体类 docx/pptx 的 JPEG 对 deflate 几乎不压缩（≈1:1），
    // 200MB 的文件解压总量很容易超 200MB 而被误拒；400MB 仍把高压缩比 bomb（1000:1）挡在可控量内。
    private static final int MAX_OOXML_TOTAL_INFLATED_BYTES = 400 * 1024 * 1024; // 累计解压上限 400MB
    private static final String NS_CONTENT_TYPES = "http://schemas.openxmlformats.org/package/2006/content-types";
    private static final String NS_WORDPROCESSINGML = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String NS_PRESENTATIONML = "http://schemas.openxmlformats.org/presentationml/2006/main";
    private static final String NS_PACKAGE_RELATIONSHIPS = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String CT_DOCX_MAIN = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
    private static final String CT_PPTX_MAIN = "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml";
    private static final String REL_OFFICE_DOC = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument";

    private boolean isRealOoxml(MultipartFile file, String ext) {
        boolean isDocx = "docx".equals(ext);
        String mainPartName = isDocx ? "word/document.xml" : "ppt/presentation.xml";
        String mainPartNs = isDocx ? NS_WORDPROCESSINGML : NS_PRESENTATIONML;
        String mainPartLocalName = isDocx ? "document" : "presentation";
        String expectedContentType = isDocx ? CT_DOCX_MAIN : CT_PPTX_MAIN;
        String expectedPartName = isDocx ? "/word/document.xml" : "/ppt/presentation.xml";
        String expectedRelTarget = isDocx ? "word/document.xml" : "ppt/presentation.xml";

        boolean contentTypesValid = false;
        boolean mainPartValid = false;
        // exy v6 P2#4：_rels/.rels 是 OOXML 包级关系的必需结构门。
        // 旧版（exy v5）把 .rels 当可选信号源（缺失也放行），导致攻击者只需放入声明正确的
        // [Content_Types].xml 和合法根的 word/document.xml 就能让普通 ZIP 冒充 docx/pptx。
        // 现强制：.rels 必须存在且其 officeDocument 关系必须指向主部件。
        boolean relsPresent = false;
        boolean relsValid = false;
        try (InputStream is = file.getInputStream();
             // Item 7：用计数 ZipInputStream 防止高压缩比 zip bomb 耗尽 CPU
             LimitedZipInputStream zis = new LimitedZipInputStream(is,
                     MAX_OOXML_INFLATED_PART_BYTES, MAX_OOXML_TOTAL_INFLATED_BYTES)) {
            java.util.zip.ZipEntry entry;
            int count = 0;
            while ((entry = zis.getNextEntry()) != null) {
                if (++count > MAX_OOXML_ENTRIES) {
                    break; // 超过上限，停止扫描
                }
                String name = entry.getName();
                // 保留 Zip Slip 守卫（只读内存无实际风险，但守卫已有不删）
                if (name == null || name.contains("..")) {
                    zis.closeEntry();
                    continue;
                }
                // 1. [Content_Types].xml：读出并解析 XML，校验 ContentType 声明
                //    元数据部件用 1MB 上限（P2#5），避免大 XML 经 DOM 放大造成堆压力。
                if ("[Content_Types].xml".equals(name)) {
                    byte[] bytes = readBoundedBytes(zis, MAX_OOXML_METADATA_PART_BYTES, true);
                    if (bytes != null && bytes.length > 0
                            && isValidContentTypesXml(bytes, expectedContentType, expectedPartName)) {
                        contentTypesValid = true;
                    }
                }
                // 2. 主部件：完整解析前缀 XML，校验根元素命名空间（未闭合即拒）
                else if (mainPartName.equals(name)) {
                    byte[] bytes = readBoundedBytes(zis, MAX_OOXML_PART_BYTES, false);
                    if (bytes != null && bytes.length > 0
                            && hasRootElement(bytes, mainPartNs, mainPartLocalName)) {
                        mainPartValid = true;
                    }
                }
                // 3. _rels/.rels（Item 6 补充结构门）：存在则校验指向主部件的 officeDocument 关系
                //    元数据部件用 1MB 上限（P2#5）。
                else if ("_rels/.rels".equals(name)) {
                    relsPresent = true;
                    byte[] bytes = readBoundedBytes(zis, MAX_OOXML_METADATA_PART_BYTES, true);
                    if (bytes != null && bytes.length > 0
                            && isValidRelsXml(bytes, expectedRelTarget)) {
                        relsValid = true;
                    }
                }
                zis.closeEntry();
                // 三项全部命中才提前返回。⚠️ 不能允许"尚未扫到 _rels/.rels"时提前返回——
                // zip 条目顺序由创建者（攻击者）控制，.rels 排在主部件之后时会把非法 .rels 跳过校验。
                // .rels 缺失（允许）时走完整扫描，最终由下方统一判定。
                if (contentTypesValid && mainPartValid && relsPresent && relsValid) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false; // 读取失败、非合法 ZIP、或 zip bomb 触发解压上限 → 视为非 OOXML
        }
        // exy v6 P2#4：强制三项齐备且均合法。删除旧版".rels 缺失也放行"的宽松 fallback，
        // 否则攻击者可构造只含 [Content_Types].xml + 合法根主部件的普通 ZIP 冒充 OOXML。
        // 四项中任一缺失或不合法即视为非 OOXML。
        return contentTypesValid && mainPartValid && relsPresent && relsValid;
    }

    /**
     * 读 ZipInputStream 当前条目的至多 maxBytes 字节作为前缀返回。
     * rejectIfOver=true（元数据部件 [Content_Types].xml / _rels/.rels 用，exy v6 P2#5）：
     *   条目超过 maxBytes 时立即抛 IOException 终止整个 ZIP 流。旧版读满前缀后由 closeEntry
     *   排空剩余数据，而 closeEntry 的解压量只受外层 LimitedZipInputStream 计数保护
     *   （单条目可到 50MB、累计 400MB），超大元数据会白耗解压 CPU；1MB 内已远超任何合法
     *   OOXML 包级元数据大小，超限即弃，不再排空（exy v7 P2#3）。
     * rejectIfOver=false（主部件用）：超过 maxBytes 时返回已读前缀，剩余字节由后续
     *   closeEntry 跳过（保持 exy v5 的 50MB 前缀截断语义，不拒合法大文档）。
     * 真实 IO 错误与超限同样以 IOException 向上抛，由 isRealOoxml 统一 catch 视为非 OOXML。
     */
    byte[] readBoundedBytes(InputStream in, int maxBytes, boolean rejectIfOver) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int total = 0;
        int n;
        while (total < maxBytes && (n = in.read(buf)) > 0) {
            int toWrite = Math.min(n, maxBytes - total);
            baos.write(buf, 0, toWrite);
            total += toWrite;
        }
        if (rejectIfOver && total >= maxBytes) {
            // 条目可能仍有数据：探测一次。若还能读到字节说明确实超限，立即抛异常终止整个流
            // （不调用 closeEntry 排空超大元数据条目，避免白耗解压）。
            if (in.read(buf) > 0) {
                throw new IOException("OOXML 元数据部件超过 " + maxBytes + " 字节上限，终止解压");
            }
        }
        return baos.toByteArray();
    }

    /**
     * 校验 _rels/.rels：良构 XML + 正确命名空间 + 含指向主部件的 officeDocument Relationship。
     * exy v5 Item 6 补充结构门——_rels/.rels 是 OOXML 包级关系部件，其 officeDocument 关系
     * 指向主部件（word/document.xml 或 ppt/presentation.xml），是区分"任意 ZIP"的标志性结构。
     */
    private boolean isValidRelsXml(byte[] xml, String expectedTarget) {
        Document doc = parseSecureXml(xml);
        if (doc == null) return false;
        Element root = doc.getDocumentElement();
        if (root == null || !NS_PACKAGE_RELATIONSHIPS.equals(root.getNamespaceURI()) || !"Relationships".equals(root.getLocalName())) {
            return false;
        }
        NodeList relationships = root.getElementsByTagNameNS(NS_PACKAGE_RELATIONSHIPS, "Relationship");
        for (int i = 0; i < relationships.getLength(); i++) {
            Element rel = (Element) relationships.item(i);
            String type = rel.getAttribute("Type");
            String target = rel.getAttribute("Target");
            // Target 可能是相对路径（word/document.xml）或绝对路径（/word/document.xml），都接受
            if (REL_OFFICE_DOC.equals(type)
                    && (expectedTarget.equals(target) || ("/" + expectedTarget).equals(target))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验 [Content_Types].xml：良构 XML + 正确命名空间 + 含对应扩展名的 ContentType 声明。
     * OOXML 的 ContentType 既能以 Default（按扩展名）也能以 Override（按 PartName）声明，
     * 故同时检查两者：Default 的 Extension 匹配，或 Override 的 PartName 匹配主部件路径。
     */
    private boolean isValidContentTypesXml(byte[] xml, String expectedContentType, String expectedPartName) {
        Document doc = parseSecureXml(xml);
        if (doc == null) return false;
        Element root = doc.getDocumentElement();
        if (root == null || !NS_CONTENT_TYPES.equals(root.getNamespaceURI()) || !"Types".equals(root.getLocalName())) {
            return false;
        }
        NodeList defaults = root.getElementsByTagNameNS(NS_CONTENT_TYPES, "Default");
        for (int i = 0; i < defaults.getLength(); i++) {
            Element el = (Element) defaults.item(i);
            // docx/pptx 的主部件都是 .xml 扩展名，Default Extension="xml" + 主 ContentType 命中即可
            if ("xml".equals(el.getAttribute("Extension"))
                    && expectedContentType.equals(el.getAttribute("ContentType"))) {
                return true;
            }
        }
        NodeList overrides = root.getElementsByTagNameNS(NS_CONTENT_TYPES, "Override");
        for (int i = 0; i < overrides.getLength(); i++) {
            Element el = (Element) overrides.item(i);
            if (expectedPartName.equals(el.getAttribute("PartName"))
                    && expectedContentType.equals(el.getAttribute("ContentType"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验 XML 根元素命名空间与 localName 匹配（不比前缀，比 getNamespaceURI）。
     * exy v5 Item 6：SAX 流式完整解析整个字节流（不构建 DOM、不提前终止），
     * 未闭合的 XML 会在 EOF 处抛 SAXParseException 被拒——堵住旧版"读根标签即
     * StopParsingException 终止"的绕过，同时避免大主部件 DOM 解析的内存峰值。
     * 代价：document.xml 总大小超过 MAX_OOXML_PART_BYTES（50MB）的合法文档会被
     * 前缀截断误拒（属可接受边缘，与计划一致）。
     */
    private boolean hasRootElement(byte[] xml, String expectedNs, String expectedLocalName) {
        final boolean[] rootMatched = {false};
        try {
            javax.xml.parsers.SAXParserFactory factory = newSecureSaxFactory();
            factory.setNamespaceAware(true);
            org.xml.sax.helpers.DefaultHandler handler = new org.xml.sax.helpers.DefaultHandler() {
                private boolean rootSeen = false;
                @Override
                public void startElement(String uri, String localName, String qName,
                                         org.xml.sax.Attributes attrs) {
                    if (!rootSeen) {
                        rootSeen = true;
                        rootMatched[0] = expectedNs.equals(uri) && expectedLocalName.equals(localName);
                    }
                }
            };
            factory.newSAXParser().parse(new java.io.ByteArrayInputStream(xml), handler);
            // 解析完整走到 EOF（无提前终止）：未闭合/截断的文档在此处已抛 SAXParseException 被拒
            return rootMatched[0];
        } catch (org.xml.sax.SAXException | IOException | javax.xml.parsers.ParserConfigurationException e) {
            return false; // 非良构 XML（含未闭合截断）或解析失败
        }
    }

    /** 构造带 XXE 防护的 SAXParserFactory（与 parseSecureXml 的防护等价） */
    private javax.xml.parsers.SAXParserFactory newSecureSaxFactory() {
        javax.xml.parsers.SAXParserFactory factory = javax.xml.parsers.SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        try { factory.setFeature("http://javax.xml.XMLConstants/feature/secure-processing", true); } catch (Exception ignored) {}
        try { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignored) {}
        try { factory.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignored) {}
        try { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignored) {}
        try { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false); } catch (Exception ignored) {}
        return factory;
    }

    /**
     * 解析 XML 字节为 Document，带完整 XXE 防护。
     * 解析的是攻击者可控的上传内容，必须禁 DOCTYPE + 外部实体 + DTD/Schema 访问。
     */
    private Document parseSecureXml(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://javax.xml.XMLConstants/feature/secure-processing", true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); // 最强：完全禁 DOCTYPE
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            trySetXmlAttribute(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
            trySetXmlAttribute(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            DocumentBuilder builder = factory.newDocumentBuilder();
            // 静默吞 DTD/实体相关错误 → 视为不合法
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            return builder.parse(new java.io.ByteArrayInputStream(xml));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            return null;
        }
    }

    /** setAttribute 部分实现不支持（抛 IllegalArgumentException），包 try/catch 忽略 */
    private void trySetXmlAttribute(DocumentBuilderFactory factory, String key, String value) {
        try {
            factory.setAttribute(key, value);
        } catch (IllegalArgumentException ignored) {
            // 实现（如 Java 内置 Xerces）不识别该属性，忽略
        }
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        return matchBytes(data, 0, prefix);
    }

    private boolean matchBytes(byte[] data, int offset, byte[] pattern) {
        if (data.length < offset + pattern.length) return false;
        for (int index = 0; index < pattern.length; index++) {
            if (data[offset + index] != pattern[index]) return false;
        }
        return true;
    }

    private Path resolveSafe(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            throw new IllegalArgumentException("相对目录不能为空");
        }
        Path resolved = uploadRoot.resolve(relativePath).toAbsolutePath().normalize();
        ensureInsideRoot(resolved);
        return resolved;
    }

    private void ensureInsideRoot(Path path) {
        if (!path.startsWith(uploadRoot)) {
            throw new StorageException("非法文件路径: " + path);
        }
    }

    private void ensureExistingSource(Path source) {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IllegalArgumentException("源文件不存在");
        }
        ensureInsideRoot(source.toAbsolutePath().normalize());
    }

    private static String sanitizeExtension(String extension) {
        if (extension == null) {
            return "";
        }
        String safe = extension.toLowerCase().replaceAll("[^a-z0-9]", "");
        return safe.length() > 10 ? safe.substring(0, 10) : safe;
    }

    private void checkDirectoryPermissions() throws IOException {
        Path testFile = uploadRoot.resolve(".permission-test");
        try {
            Files.createFile(testFile);
            Files.delete(testFile);
        } catch (AccessDeniedException e) {
            throw new IOException("上传目录不可写: " + uploadRoot, e);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException cleanupError) {
            log.warn("清理上传暂存文件失败: {}", path, cleanupError);
        }
    }

    /**
     * 计数 ZipInputStream：限制每条目解压后字节数 + 全部条目累计解压字节数，防 zip bomb。
     * exy v5 Item 7：closeEntry() 会排空当前条目剩余解压数据，高压缩比单条目不触发条目数限制，
     * 仍耗 CPU。本类重写 read 累计解压字节，超限抛 IOException 终止整个流
     * （覆盖 readBoundedBytes 读取和 closeEntry 排空两条路径）。
     * 包可见（非 private）：单元测试用小上限直测计数逻辑，避免构造数百 MB 载荷。
     */
    static class LimitedZipInputStream extends java.util.zip.ZipInputStream {
        private final int maxPerEntry;
        private final int maxTotal;
        private long currentEntryInflated = 0;
        private long totalInflated = 0;

        LimitedZipInputStream(InputStream in, int maxPerEntry, int maxTotal) {
            super(in);
            this.maxPerEntry = maxPerEntry;
            this.maxTotal = maxTotal;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1) {
                account(1);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) {
                account(n);
            }
            return n;
        }

        private void account(int n) throws IOException {
            currentEntryInflated += n;
            totalInflated += n;
            if (currentEntryInflated > maxPerEntry) {
                throw new IOException("ZIP 条目解压超过单条目上限 " + maxPerEntry + " 字节（疑似 zip bomb）");
            }
            if (totalInflated > maxTotal) {
                throw new IOException("ZIP 累计解压超过总量上限 " + maxTotal + " 字节（疑似 zip bomb）");
            }
        }

        @Override
        public java.util.zip.ZipEntry getNextEntry() throws IOException {
            currentEntryInflated = 0; // 每条目计数重置
            return super.getNextEntry();
        }
    }

    // 自定义异常
    public static class StorageException extends RuntimeException {
        public StorageException(String message) {
            super(message);
        }

        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
