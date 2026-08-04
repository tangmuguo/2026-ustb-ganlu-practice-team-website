package com.vihu.ganlu.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.StandardCopyOption;
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

            // 统一为 URL 风格的相对路径（正斜杠），避免 Windows 下反斜杠路径
            // 让后续 moveImageByStatus 的 startsWith("images_pending/") 前缀判断失效。
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
     * 在 uploadRoot 下把文件从 fromRelativePath 移动到 toRelativePath。
     * 用于团队风采图片审核状态切换时在私有目录(images_pending)与公开目录(images)之间搬运，
     * 保证非公开文件物理上不可通过静态资源地址访问。
     *
     * @param fromRelativePath 源相对路径（uploadRoot 下）
     * @param toRelativePath   目标相对路径（uploadRoot 下）
     * @return 移动后的目标绝对路径
     * @throws StorageException 源不存在、目标已存在、或路径越界时抛出
     */
    public Path moveFile(String fromRelativePath, String toRelativePath) {
        Path from = loadFile(fromRelativePath); // 内含 startsWith(uploadRoot) 校验
        Path to = uploadRoot.resolve(toRelativePath).toAbsolutePath().normalize();
        if (!to.startsWith(uploadRoot)) {
            throw new StorageException("非法的目标路径: " + toRelativePath);
        }
        if (!Files.exists(from)) {
            throw new StorageException("源文件不存在: " + fromRelativePath);
        }
        if (Files.exists(to)) {
            throw new StorageException("目标文件已存在: " + toRelativePath);
        }
        try {
            Files.createDirectories(to.getParent());
            return Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new StorageException("移动文件失败: " + fromRelativePath + " -> " + toRelativePath, e);
        }
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
    // F2 review: 校验只需根元素（XML 声明 + 根标签 + ContentType 列表），读前 64KB 前缀即可，
    // 无需读满整个部件。这样兼容主部件超 5MB 的合法 docx（大量修订/内嵌内容），同时仍防 zip bomb。
    private static final int MAX_OOXML_PART_BYTES = 64 * 1024; // 单条目最多读 64KB 前缀
    private static final String NS_CONTENT_TYPES = "http://schemas.openxmlformats.org/package/2006/content-types";
    private static final String NS_WORDPROCESSINGML = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String NS_PRESENTATIONML = "http://schemas.openxmlformats.org/presentationml/2006/main";
    private static final String CT_DOCX_MAIN = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
    private static final String CT_PPTX_MAIN = "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml";

    private boolean isRealOoxml(MultipartFile file, String ext) {
        boolean isDocx = "docx".equals(ext);
        String mainPartName = isDocx ? "word/document.xml" : "ppt/presentation.xml";
        String mainPartNs = isDocx ? NS_WORDPROCESSINGML : NS_PRESENTATIONML;
        String mainPartLocalName = isDocx ? "document" : "presentation";
        String expectedContentType = isDocx ? CT_DOCX_MAIN : CT_PPTX_MAIN;
        String expectedPartName = isDocx ? "/word/document.xml" : "/ppt/presentation.xml";

        boolean contentTypesValid = false;
        boolean mainPartValid = false;
        try (InputStream is = file.getInputStream();
             java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(is)) {
            java.util.zip.ZipEntry entry;
            int count = 0;
            while ((entry = zis.getNextEntry()) != null) {
                if (++count > MAX_OOXML_ENTRIES) {
                    break; // 超过上限，停止扫描（防 zip bomb）
                }
                String name = entry.getName();
                // 保留 Zip Slip 守卫（只读内存无实际风险，但守卫已有不删）
                if (name == null || name.contains("..")) {
                    zis.closeEntry();
                    continue;
                }
                // 1. [Content_Types].xml：读出并解析 XML，校验 ContentType 声明
                if ("[Content_Types].xml".equals(name)) {
                    byte[] bytes = readBoundedBytes(zis, MAX_OOXML_PART_BYTES);
                    if (bytes != null && bytes.length > 0
                            && isValidContentTypesXml(bytes, expectedContentType, expectedPartName)) {
                        contentTypesValid = true;
                    }
                }
                // 2. 主部件：读出非空字节 + 解析 XML，校验根元素命名空间
                else if (mainPartName.equals(name)) {
                    byte[] bytes = readBoundedBytes(zis, MAX_OOXML_PART_BYTES);
                    if (bytes != null && bytes.length > 0
                            && hasRootElement(bytes, mainPartNs, mainPartLocalName)) {
                        mainPartValid = true;
                    }
                }
                zis.closeEntry();
                // 两目标都命中即可提前返回（语义等价于扫完，但更省）
                if (contentTypesValid && mainPartValid) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false; // 读取失败或非合法 ZIP → 视为非 OOXML
        }
        return false;
    }

    /**
     * 读 ZipInputStream 当前条目的至多 maxBytes 字节作为前缀返回。
     * 条目超过 maxBytes 时返回已读前缀，不再视为超限拒绝——调用方只取根元素，
     * 前缀已足够；剩余字节由后续 closeEntry 跳过。否则主部件超过上限的合法
     * docx/pptx 会被误拒。仅真实 IO 错误返回 null。
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
     * 校验 XML 流根元素命名空间与 localName 匹配（不比前缀，比 getNamespaceURI）。
     * F2 review: 用 SAX 流式解析，遇到根元素 startElement 即停止——不读完整文档，
     * 这样兼容主部件超 5MB 的合法 docx（DocumentBuilder.parse 截断流会报错，SAX 不会）。
     */
    private boolean hasRootElement(byte[] xml, String expectedNs, String expectedLocalName) {
        final String[] rootNs = {null};
        final String[] rootLocal = {null};
        try {
            javax.xml.parsers.SAXParserFactory factory = newSecureSaxFactory();
            factory.setNamespaceAware(true);
            org.xml.sax.helpers.DefaultHandler handler = new org.xml.sax.helpers.DefaultHandler() {
                @Override
                public void startElement(String uri, String localName, String qName, org.xml.sax.Attributes attrs) {
                    rootNs[0] = uri;
                    rootLocal[0] = localName;
                    throw new StopParsingException(); // 读到根元素即停，不继续解析
                }
            };
            factory.newSAXParser().parse(new java.io.ByteArrayInputStream(xml), handler);
            // parse 正常结束（无根元素，空文档）→ rootNs[0] 仍为 null
            return expectedNs.equals(rootNs[0]) && expectedLocalName.equals(rootLocal[0]);
        } catch (StopParsingException stop) {
            // 预期终止：已读到根元素
            return expectedNs.equals(rootNs[0]) && expectedLocalName.equals(rootLocal[0]);
        } catch (org.xml.sax.SAXException | IOException | javax.xml.parsers.ParserConfigurationException e) {
            // 非良构 XML 或解析失败
            return false;
        }
    }

    /** SAX 解析读到根元素后抛此异常提前终止，避免读完整个大文档（独立 RuntimeException，不与 SAXException 冲突） */
    private static class StopParsingException extends RuntimeException {
        StopParsingException() { super("stop after root element"); }
    }

    /** 构造带 XXE 防护的 SAXParserFactory（与 DocumentBuilderFactory 等价的防护） */
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
