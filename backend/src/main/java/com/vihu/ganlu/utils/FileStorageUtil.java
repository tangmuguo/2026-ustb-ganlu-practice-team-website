package com.vihu.ganlu.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
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

    // ---- 文件类型白名单 ----
    public static final List<String> IMAGE_EXT = Arrays.asList("jpg", "jpeg", "png", "webp");
    public static final List<String> VIDEO_EXT = Arrays.asList("mp4", "mov");
    public static final List<String> DOC_EXT = Arrays.asList("pdf", "doc", "docx", "ppt", "pptx", "zip");

    // ---- 魔数签名 ----
    private static final byte[] MAGIC_JPEG = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] MAGIC_PNG = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] MAGIC_WEBP_RIFF = "RIFF".getBytes();
    private static final byte[] MAGIC_WEBP_WEBP = "WEBP".getBytes();
    private static final byte[] MAGIC_MP4_FTYP = "ftyp".getBytes();
    private static final byte[] MAGIC_PDF = "%PDF".getBytes();
    // OOXML（docx/pptx/zip）本质是 ZIP 容器，魔数为 PK\x03\x04
    private static final byte[] MAGIC_ZIP = new byte[]{0x50, 0x4B, 0x03, 0x04};
    // 老式 DOC/PPT 为 OLE Compound File，魔数为 D0 CF 11 E0 A1 B1 1A E1
    private static final byte[] MAGIC_OLE = new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    // ---- 大小限制 ----
    public static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024;      // 10MB
    public static final long MAX_VIDEO_SIZE = 200 * 1024 * 1024;     // 200MB
    public static final long MAX_DOCUMENT_SIZE = 200 * 1024 * 1024;  // 200MB

    public FileStorageUtil(
            @Value("${file.upload-dir}") String uploadDir,
            @Value("${spring.profiles.active:dev}") String activeProfile) {

        this.activeProfile = activeProfile;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(uploadRoot);
            log.info("当前运行环境: {}", this.activeProfile);
            log.info("文件上传目录已初始化:{}",uploadRoot);

            // 生产环境额外检查权限
            if ("prod".equals(this.activeProfile)) {
                checkDirectoryPermissions();
            }
        } catch (IOException e) {
            throw new RuntimeException("无法创建上传目录: " + uploadRoot, e);
        }
    }

    private void checkDirectoryPermissions() throws IOException {
        Path testFile = uploadRoot.resolve(".permission-test");
        try {
            Files.createFile(testFile);
            Files.delete(testFile);
        } catch (AccessDeniedException e) {
            String errorMsg = String.format(
                    "上传目录权限不足。请执行: sudo chown -R tomcat8:tomcat8 %s",
                    uploadRoot.getParent());
            throw new IOException(errorMsg, e);
        }
    }

    public String storeFile(MultipartFile file, String subDir) {
        try {
            // 子目录也需做边界校验，防止 subDir 中的 ../ 逃出 uploadRoot
            Path targetDir = uploadRoot.resolve(subDir).toAbsolutePath().normalize();
            if (!targetDir.startsWith(uploadRoot)) {
                throw new StorageException("非法的存储子目录: " + subDir);
            }
            Files.createDirectories(targetDir);

            // 物理文件名完全由服务端生成（UUID + 验证后的扩展名），
            // 原始文件名仅作为元数据保存到数据库，避免路径穿越和同名覆盖。
            String ext = extractExtension(file.getOriginalFilename());
            String safeFilename = UUID.randomUUID().toString()
                    + (ext.isEmpty() ? "" : "." + ext);

            Path targetPath = targetDir.resolve(safeFilename).toAbsolutePath().normalize();
            if (!targetPath.startsWith(uploadRoot)) {
                throw new StorageException("非法的存储路径: " + targetPath);
            }
            file.transferTo(targetPath);

            // 统一为 URL 风格的相对路径（正斜杠），兼容 Windows 历史反斜杠路径
            // （loadFile / 发布校验 / serveImage 都按正斜杠解析）。
            return uploadRoot.relativize(targetPath).toString().replace('\\', '/');
        } catch (IOException e) {
            throw new StorageException("存储文件失败: " + file.getOriginalFilename(), e);
        }
    }

    /**
     * 由相对路径relativePath与uploadRoot的根路径 组合成完整的绝对路径，多用于读取或者下载文件。
     * 校验结果路径必须仍在 uploadRoot 之下，防止路径穿越。
     * @param relativePath 存储时返回的相对路径
     * @return 规范化后的绝对路径
     */
    public Path loadFile(String relativePath) {
        // 兼容 DB 中可能残留的反斜杠路径（Windows 旧数据），统一成正斜杠再解析
        String normalized = relativePath == null ? "" : relativePath.replace('\\', '/');
        Path filePath = uploadRoot.resolve(normalized).toAbsolutePath().normalize();
        if (!filePath.startsWith(uploadRoot)) {
            throw new StorageException("尝试访问上传目录之外的文件: " + relativePath);
        }
        return filePath;
    }

    /**
     * 根据相对路径删除文件
     * @param relativePath 要删除文件的相对路径
     * @return 是否删除成功
     * @throws StorageException 如果删除过程中出现错误
     */
    public boolean deleteFile(String relativePath) {
        try {
            Path filePath = loadFile(relativePath);

            // 安全检查：确保文件路径在uploadRoot目录下
            if (!filePath.startsWith(uploadRoot)) {
                throw new SecurityException("尝试删除不在上传目录下的文件: " + filePath);
            }

            // 检查文件是否存在
            if (!Files.exists(filePath)) {
                log.warn("文件不存在，无法删除: {}", filePath);
                return false;
            }

            // 删除文件
            Files.delete(filePath);
            log.info("文件删除成功: {}", filePath);

            return true;
        } catch (IOException e) {
            throw new StorageException("删除文件失败: " + relativePath, e);
        }
    }

    /**
     * 递归删除空的父目录
     * @param directory 要检查的目录
     * @throws IOException 如果删除过程中出现错误
     */
    private void deleteEmptyParentDirectories(Path directory) throws IOException {
        // 确保目录在uploadRoot下
        if (directory != null && directory.startsWith(uploadRoot) && !directory.equals(uploadRoot)) {
            // 检查目录是否为空
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory)) {
                if (!dirStream.iterator().hasNext()) {
                    // 目录为空，删除它
                    Files.delete(directory);
                    log.info("已删除空目录: {}", directory);

                    // 递归检查上级目录
                    deleteEmptyParentDirectories(directory.getParent());
                }
            }
        }
    }

    // =====================================================================
    // 文件校验增强
    // =====================================================================

    public enum FileCategory {
        IMAGE, VIDEO, DOCUMENT, REJECTED
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

    /**
     * 从文件名提取小写扩展名（不含点号）。
     * @param filename 文件名，如 "photo.JPG"
     * @return 小写扩展名，如 "jpg"；无法提取时返回空字符串
     */
    public String extractExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot >= 0 && dot < filename.length() - 1) {
            return filename.substring(dot + 1).toLowerCase();
        }
        return "";
    }

    /**
     * 综合校验文件：扩展名 + MIME + 魔数 + 大小。
     * @param file 上传文件
     * @param maxSizeBytes 允许的最大字节数
     * @return 校验通过的文件信息
     * @throws IllegalArgumentException 校验失败时抛出
     */
    public ValidatedFile validate(MultipartFile file, long maxSizeBytes) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException("文件名为空");
        }

        // 1. 提取扩展名
        String ext = "";
        int dot = originalFilename.lastIndexOf('.');
        if (dot >= 0 && dot < originalFilename.length() - 1) {
            ext = originalFilename.substring(dot + 1).toLowerCase();
        }

        // 2. 大小校验
        if (file.getSize() > maxSizeBytes) {
            throw new IllegalArgumentException(
                    String.format("文件大小超过限制: %d > %d bytes", file.getSize(), maxSizeBytes));
        }

        // 3. 确定类别 + 校验扩展名白名单
        FileCategory category;
        if (IMAGE_EXT.contains(ext)) {
            category = FileCategory.IMAGE;
        } else if (VIDEO_EXT.contains(ext)) {
            category = FileCategory.VIDEO;
        } else if (DOC_EXT.contains(ext)) {
            category = FileCategory.DOCUMENT;
        } else {
            throw new IllegalArgumentException("不支持的文件类型: " + ext);
        }

        // 4. 魔数校验（防伪装）— 只读文件头，避免大文件 OOM
        try {
            byte[] header = new byte[12];
            try (InputStream is = file.getInputStream()) {
                int n = is.read(header);
                if (n < 8) {
                    throw new IllegalArgumentException("文件头过短，无法校验");
                }
            }
            if (!matchesMagic(header, category, ext)) {
                throw new IllegalArgumentException("文件内容与实际扩展名不符（魔数校验失败）");
            }
            // Item 9: docx/pptx 仅凭 ZIP 头不够（任意 ZIP 改后缀即可通过），
            // 追加 OOXML 真实结构校验：检查 ZIP 内含 [Content_Types].xml 和 word/(docx)/ppt/(pptx)。
            if (category == FileCategory.DOCUMENT && ("docx".equals(ext) || "pptx".equals(ext))) {
                if (!isRealOoxml(file, ext)) {
                    throw new IllegalArgumentException("文件不是有效的 " + ext + "（OOXML 结构校验失败）");
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("读取文件失败: " + e.getMessage());
        }

        ValidatedFile vf = new ValidatedFile();
        vf.setCategory(category);
        vf.setExtension(ext);
        vf.setMimeType(file.getContentType());
        vf.setSize(file.getSize());
        vf.setRaw(file);
        return vf;
    }

    private boolean matchesMagic(byte[] header, FileCategory category, String ext) {
        switch (category) {
            case IMAGE:
                if ("jpg".equals(ext) || "jpeg".equals(ext)) {
                    return startsWith(header, MAGIC_JPEG);
                } else if ("png".equals(ext)) {
                    return startsWith(header, MAGIC_PNG);
                } else if ("webp".equals(ext)) {
                    // RIFF....WEBP
                    return startsWith(header, MAGIC_WEBP_RIFF)
                            && header.length >= 12
                            && matchBytes(header, 8, MAGIC_WEBP_WEBP);
                }
                return false;
            case VIDEO:
                // MP4/MOV: ftyp box at offset 4
                if (header.length >= 12) {
                    return matchBytes(header, 4, MAGIC_MP4_FTYP);
                }
                return false;
            case DOCUMENT:
                if ("pdf".equals(ext)) {
                    return startsWith(header, MAGIC_PDF);
                }
                // docx/pptx/zip 均为 ZIP 容器（OOXML），校验 PK 头
                if ("docx".equals(ext) || "pptx".equals(ext) || "zip".equals(ext)) {
                    return startsWith(header, MAGIC_ZIP);
                }
                // 老式 doc/ppt 为 OLE Compound File，校验 OLE 头
                if ("doc".equals(ext) || "ppt".equals(ext)) {
                    return startsWith(header, MAGIC_OLE);
                }
                return false;
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
    private static final int MAX_OOXML_ENTRIES = 1000;
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
                    byte[] bytes = readBoundedBytes(zis, MAX_OOXML_METADATA_PART_BYTES);
                    if (bytes != null && bytes.length > 0
                            && isValidContentTypesXml(bytes, expectedContentType, expectedPartName)) {
                        contentTypesValid = true;
                    }
                }
                // 2. 主部件：完整解析前缀 XML，校验根元素命名空间（未闭合即拒）
                else if (mainPartName.equals(name)) {
                    byte[] bytes = readBoundedBytes(zis, MAX_OOXML_PART_BYTES);
                    if (bytes != null && bytes.length > 0
                            && hasRootElement(bytes, mainPartNs, mainPartLocalName)) {
                        mainPartValid = true;
                    }
                }
                // 3. _rels/.rels（Item 6 补充结构门）：存在则校验指向主部件的 officeDocument 关系
                //    元数据部件用 1MB 上限（P2#5）。
                else if ("_rels/.rels".equals(name)) {
                    relsPresent = true;
                    byte[] bytes = readBoundedBytes(zis, MAX_OOXML_METADATA_PART_BYTES);
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
     * 条目超过 maxBytes 时返回已读前缀，不再视为超限拒绝——剩余字节由后续 closeEntry 跳过
     * （closeEntry 的解压量受外层 LimitedZipInputStream 的计数上限保护）。
     * 仅真实 IO 错误返回 null。
     */
    private byte[] readBoundedBytes(InputStream in, int maxBytes) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int total = 0;
        int n;
        try {
            while ((n = in.read(buf)) > 0) {
                if (total >= maxBytes) {
                    break; // 前缀已够，停止读取（剩余数据由 closeEntry 跳过）
                }
                int toWrite = Math.min(n, maxBytes - total);
                baos.write(buf, 0, toWrite);
                total += toWrite;
            }
        } catch (IOException e) {
            return null;
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
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    private boolean matchBytes(byte[] data, int offset, byte[] pattern) {
        if (data.length < offset + pattern.length) return false;
        for (int i = 0; i < pattern.length; i++) {
            if (data[offset + i] != pattern[i]) return false;
        }
        return true;
    }

    // ---- 便捷方法 ----
    public ValidatedFile isAllowedImage(MultipartFile file) {
        return validate(file, MAX_IMAGE_SIZE);
    }

    public ValidatedFile isAllowedVideo(MultipartFile file) {
        return validate(file, MAX_VIDEO_SIZE);
    }

    public ValidatedFile isAllowedDocument(MultipartFile file) {
        return validate(file, MAX_DOCUMENT_SIZE);
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
