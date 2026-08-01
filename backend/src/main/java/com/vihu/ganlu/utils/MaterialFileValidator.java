package com.vihu.ganlu.utils;

import com.vihu.ganlu.entitys.UploadedFileInfo;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Component
public class MaterialFileValidator {
    public static final long MAX_MATERIAL_SIZE = 200L * 1024L * 1024L;
    public static final long MAX_COVER_SIZE = 10L * 1024L * 1024L;
    public static final long MAX_CHUNK_SIZE = 6L * 1024L * 1024L;

    private static final Set<String> MATERIAL_EXTENSIONS = new HashSet<>(Arrays.asList(
            "pdf", "ppt", "pptx", "jpg", "jpeg", "png", "webp"
    ));
    private static final Set<String> COVER_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png"
    ));
    private static final String PRESENTATION_PART = "ppt/presentation.xml";
    private static final String PRESENTATION_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml";
    private static final String OFFICE_DOCUMENT_RELATIONSHIP =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument";
    private static final String STRICT_OFFICE_DOCUMENT_RELATIONSHIP =
            "http://purl.oclc.org/ooxml/officeDocument/relationships/officeDocument";
    private static final String PRESENTATION_NAMESPACE =
            "http://schemas.openxmlformats.org/presentationml/2006/main";
    private static final String STRICT_PRESENTATION_NAMESPACE =
            "http://purl.oclc.org/ooxml/presentationml/main";
    private static final int MAX_XML_ENTRY_SIZE = 1024 * 1024;

    public UploadedFileInfo validate(Path file, String originalName, String purpose, long expectedSize)
            throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("上传文件不存在");
        }
        String normalizedPurpose = normalizePurpose(purpose);
        String safeName = FileStorageUtil.safeLeafName(originalName);
        String extension = FileStorageUtil.extensionOf(safeName);
        Set<String> allowed = "COVER".equals(normalizedPurpose) ? COVER_EXTENSIONS : MATERIAL_EXTENSIONS;
        if (!allowed.contains(extension)) {
            throw new IllegalArgumentException("不支持的文件扩展名: " + extension);
        }

        long size = Files.size(file);
        long maxSize = "COVER".equals(normalizedPurpose) ? MAX_COVER_SIZE : MAX_MATERIAL_SIZE;
        if (size <= 0 || size > maxSize) {
            throw new IllegalArgumentException("文件大小不合法");
        }
        if (expectedSize > 0 && size != expectedSize) {
            throw new IllegalArgumentException("文件大小校验失败");
        }

        String mimeType = validateSignature(file, extension);
        UploadedFileInfo info = new UploadedFileInfo();
        info.setOriginalName(safeName);
        info.setExtension(extension);
        info.setMimeType(mimeType);
        info.setChecksum(md5(file));
        info.setSize(size);
        info.setPurpose(normalizedPurpose);
        return info;
    }

    public String normalizePurpose(String purpose) {
        String normalized = purpose == null ? "" : purpose.trim().toUpperCase(Locale.ROOT);
        if (!"COVER".equals(normalized) && !"MATERIAL".equals(normalized)) {
            throw new IllegalArgumentException("上传用途必须是 COVER 或 MATERIAL");
        }
        return normalized;
    }

    private String validateSignature(Path file, String extension) throws IOException {
        byte[] header = new byte[12];
        int length;
        try (InputStream input = Files.newInputStream(file)) {
            length = input.read(header);
        }
        if (length < 4) {
            throw new IllegalArgumentException("文件内容无效");
        }

        switch (extension) {
            case "pdf":
                require(startsWith(header, length, new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D}));
                return "application/pdf";
            case "ppt":
                require(isLegacyPowerPoint(file));
                return "application/vnd.ms-powerpoint";
            case "pptx":
                require(isPptx(file));
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "jpg":
            case "jpeg":
                require((header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8 && (header[2] & 0xff) == 0xff);
                return "image/jpeg";
            case "png":
                require(startsWith(header, length, new byte[]{
                        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
                }));
                return "image/png";
            case "webp":
                require(length >= 12
                        && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                        && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P');
                return "image/webp";
            default:
                throw new IllegalArgumentException("不支持的文件类型");
        }
    }

    private boolean isPptx(Path file) {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            Document contentTypes = readXml(zip, "[Content_Types].xml");
            Document packageRelationships = readXml(zip, "_rels/.rels");
            Document presentation = readXml(zip, PRESENTATION_PART);
            Document presentationRelationships = readXml(zip, "ppt/_rels/presentation.xml.rels");
            return hasPresentationContentType(contentTypes)
                    && hasPresentationRootRelationship(packageRelationships)
                    && isPresentationDocument(presentation)
                    && isRelationshipsDocument(presentationRelationships);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isLegacyPowerPoint(Path file) {
        try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "r")) {
            return new CompoundFileReader(input).hasReadableStream("PowerPoint Document");
        } catch (IOException | IllegalArgumentException e) {
            return false;
        }
    }

    private Document readXml(ZipFile zip, String entryName) throws Exception {
        ZipEntry entry = zip.getEntry(entryName);
        if (entry == null || entry.isDirectory() || entry.getSize() > MAX_XML_ENTRY_SIZE) {
            throw new IOException("PPTX 缺少必要结构: " + entryName);
        }
        byte[] content;
        try (InputStream input = zip.getInputStream(entry);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) {
                    continue;
                }
                total += count;
                if (total > MAX_XML_ENTRY_SIZE) {
                    throw new IOException("PPTX XML 条目过大");
                }
                output.write(buffer, 0, count);
            }
            content = output.toByteArray();
        }

        DocumentBuilderFactory factory = secureDocumentBuilderFactory();
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(content));
    }

    private DocumentBuilderFactory secureDocumentBuilderFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return factory;
    }

    private boolean hasPresentationContentType(Document document) {
        NodeList overrides = document.getElementsByTagNameNS("*", "Override");
        for (int index = 0; index < overrides.getLength(); index++) {
            Element element = (Element) overrides.item(index);
            String partName = element.getAttribute("PartName").replaceFirst("^/", "");
            if (PRESENTATION_PART.equals(partName)
                    && PRESENTATION_CONTENT_TYPE.equals(element.getAttribute("ContentType"))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPresentationRootRelationship(Document document) {
        NodeList relationships = document.getElementsByTagNameNS("*", "Relationship");
        for (int index = 0; index < relationships.getLength(); index++) {
            Element element = (Element) relationships.item(index);
            String type = element.getAttribute("Type");
            String target = element.getAttribute("Target").replace('\\', '/').replaceFirst("^/", "");
            if ((OFFICE_DOCUMENT_RELATIONSHIP.equals(type) || STRICT_OFFICE_DOCUMENT_RELATIONSHIP.equals(type))
                    && PRESENTATION_PART.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPresentationDocument(Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !"presentation".equals(root.getLocalName())) {
            return false;
        }
        String namespace = root.getNamespaceURI();
        return PRESENTATION_NAMESPACE.equals(namespace) || STRICT_PRESENTATION_NAMESPACE.equals(namespace);
    }

    private boolean isRelationshipsDocument(Document document) {
        Element root = document.getDocumentElement();
        return root != null && "Relationships".equals(root.getLocalName());
    }

    private boolean startsWith(byte[] actual, int actualLength, byte[] expected) {
        if (actualLength < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (actual[i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new IllegalArgumentException("文件扩展名与实际内容不匹配");
        }
    }

    private String md5(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            try (InputStream input = Files.newInputStream(file)) {
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (count > 0) {
                        digest.update(buffer, 0, count);
                    }
                }
            }
            StringBuilder result = new StringBuilder(32);
            for (byte value : digest.digest()) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 Java 环境不支持 MD5", e);
        }
    }

    /**
     * Minimal, dependency-free Compound File Binary reader.  A shared CFB magic header is not
     * enough to distinguish legacy Word/Excel files from PowerPoint, so the directory and the
     * PowerPoint-specific stream chain are validated here.
     */
    private static final class CompoundFileReader {
        private static final byte[] SIGNATURE = new byte[]{
                (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
        };
        private static final int FREE_SECTOR = -1;
        private static final int END_OF_CHAIN = -2;
        private static final int MAX_DIRECTORY_BYTES = 8 * 1024 * 1024;

        private final RandomAccessFile input;
        private final byte[] header = new byte[512];
        private final int sectorSize;
        private final int miniSectorSize;
        private final int totalSectors;
        private final int firstDirectorySector;
        private final int miniStreamCutoff;
        private final int firstMiniFatSector;
        private final int miniFatSectorCount;
        private final int[] fat;

        private CompoundFileReader(RandomAccessFile input) throws IOException {
            this.input = input;
            if (input.length() < header.length) {
                throw new IllegalArgumentException("CFB 文件过短");
            }
            input.seek(0L);
            input.readFully(header);
            if (!matches(header, SIGNATURE) || unsignedShort(header, 28) != 0xfffe) {
                throw new IllegalArgumentException("CFB 文件头无效");
            }

            int majorVersion = unsignedShort(header, 26);
            int sectorShift = unsignedShort(header, 30);
            int miniSectorShift = unsignedShort(header, 32);
            if (!((majorVersion == 3 && sectorShift == 9) || (majorVersion == 4 && sectorShift == 12))
                    || miniSectorShift != 6) {
                throw new IllegalArgumentException("CFB 扇区参数无效");
            }
            sectorSize = 1 << sectorShift;
            miniSectorSize = 1 << miniSectorShift;
            if (input.length() % sectorSize != 0) {
                throw new IllegalArgumentException("CFB 文件长度无效");
            }
            long sectorCount = input.length() / sectorSize - 1L;
            if (sectorCount <= 0 || sectorCount > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("CFB 扇区数量无效");
            }
            totalSectors = (int) sectorCount;
            firstDirectorySector = intAt(header, 48);
            miniStreamCutoff = intAt(header, 56);
            firstMiniFatSector = intAt(header, 60);
            miniFatSectorCount = intAt(header, 64);
            fat = readFat(intAt(header, 44), intAt(header, 68), intAt(header, 72));
        }

        private boolean hasReadableStream(String expectedName) throws IOException {
            DirectoryEntry root = null;
            DirectoryEntry target = null;
            int sector = firstDirectorySector;
            int directoryBytes = 0;
            Set<Integer> visited = new HashSet<>();
            while (sector != END_OF_CHAIN) {
                requireSector(sector);
                if (!visited.add(sector) || directoryBytes > MAX_DIRECTORY_BYTES) {
                    throw new IllegalArgumentException("CFB 目录链无效");
                }
                byte[] data = readSector(sector);
                directoryBytes += data.length;
                for (int offset = 0; offset + 128 <= data.length; offset += 128) {
                    DirectoryEntry entry = readDirectoryEntry(data, offset);
                    if (entry == null) {
                        continue;
                    }
                    if (entry.type == 5) {
                        root = entry;
                    } else if (entry.type == 2 && expectedName.equals(entry.name)) {
                        target = entry;
                    }
                }
                if (root != null && target != null) {
                    break;
                }
                sector = fatNext(sector);
            }
            if (root == null || target == null || target.size <= 0) {
                return false;
            }
            if (target.size < miniStreamCutoff) {
                return isReadableMiniStream(target, root);
            }
            return isReadableRegularStream(target.startSector, target.size);
        }

        private int[] readFat(int fatSectorCount, int firstDifatSector, int difatSectorCount) throws IOException {
            if (fatSectorCount <= 0 || fatSectorCount > totalSectors
                    || difatSectorCount < 0 || difatSectorCount > totalSectors) {
                throw new IllegalArgumentException("CFB FAT 参数无效");
            }
            List<Integer> fatSectors = new ArrayList<>();
            for (int index = 0; index < 109 && fatSectors.size() < fatSectorCount; index++) {
                int sector = intAt(header, 76 + index * 4);
                if (sector >= 0) {
                    fatSectors.add(sector);
                } else if (sector != FREE_SECTOR) {
                    throw new IllegalArgumentException("CFB DIFAT 表无效");
                }
            }

            int difatSector = firstDifatSector;
            Set<Integer> visitedDifat = new HashSet<>();
            for (int chainIndex = 0; chainIndex < difatSectorCount; chainIndex++) {
                requireSector(difatSector);
                if (!visitedDifat.add(difatSector)) {
                    throw new IllegalArgumentException("CFB DIFAT 链循环");
                }
                byte[] data = readSector(difatSector);
                int entries = sectorSize / 4 - 1;
                for (int index = 0; index < entries && fatSectors.size() < fatSectorCount; index++) {
                    int sector = intAt(data, index * 4);
                    if (sector >= 0) {
                        fatSectors.add(sector);
                    } else if (sector != FREE_SECTOR) {
                        throw new IllegalArgumentException("CFB DIFAT 项无效");
                    }
                }
                difatSector = intAt(data, entries * 4);
            }
            if (fatSectors.size() != fatSectorCount) {
                throw new IllegalArgumentException("CFB FAT 扇区不完整");
            }

            int entriesPerSector = sectorSize / 4;
            int[] result = new int[fatSectorCount * entriesPerSector];
            int resultIndex = 0;
            Set<Integer> uniqueFatSectors = new HashSet<>();
            for (Integer sector : fatSectors) {
                requireSector(sector);
                if (!uniqueFatSectors.add(sector)) {
                    throw new IllegalArgumentException("CFB FAT 扇区重复");
                }
                byte[] data = readSector(sector);
                for (int index = 0; index < entriesPerSector; index++) {
                    result[resultIndex++] = intAt(data, index * 4);
                }
            }
            return result;
        }

        private boolean isReadableRegularStream(int startSector, long size) {
            long requiredSectors = (size + sectorSize - 1L) / sectorSize;
            if (requiredSectors <= 0 || requiredSectors > totalSectors) {
                return false;
            }
            int sector = startSector;
            Set<Integer> visited = new HashSet<>();
            for (long index = 0; index < requiredSectors; index++) {
                if (!isSector(sector) || !visited.add(sector)) {
                    return false;
                }
                sector = fatNext(sector);
                if (index + 1 < requiredSectors && sector == END_OF_CHAIN) {
                    return false;
                }
            }
            return sector == END_OF_CHAIN;
        }

        private boolean isReadableMiniStream(DirectoryEntry stream, DirectoryEntry root) throws IOException {
            if (miniFatSectorCount <= 0 || firstMiniFatSector < 0
                    || root.size <= 0 || !isReadableRegularStream(root.startSector, root.size)) {
                return false;
            }
            int entriesPerSector = sectorSize / 4;
            int[] miniFat = new int[miniFatSectorCount * entriesPerSector];
            int sector = firstMiniFatSector;
            int outputIndex = 0;
            Set<Integer> visited = new HashSet<>();
            for (int chainIndex = 0; chainIndex < miniFatSectorCount; chainIndex++) {
                if (!isSector(sector) || !visited.add(sector)) {
                    return false;
                }
                byte[] data = readSector(sector);
                for (int index = 0; index < entriesPerSector; index++) {
                    miniFat[outputIndex++] = intAt(data, index * 4);
                }
                sector = fatNext(sector);
            }
            if (sector != END_OF_CHAIN) {
                return false;
            }

            long requiredMiniSectors = (stream.size + miniSectorSize - 1L) / miniSectorSize;
            int miniSector = stream.startSector;
            Set<Integer> visitedMini = new HashSet<>();
            for (long index = 0; index < requiredMiniSectors; index++) {
                if (miniSector < 0 || miniSector >= miniFat.length || !visitedMini.add(miniSector)
                        || (long) miniSector * miniSectorSize >= root.size) {
                    return false;
                }
                miniSector = miniFat[miniSector];
                if (index + 1 < requiredMiniSectors && miniSector == END_OF_CHAIN) {
                    return false;
                }
            }
            return miniSector == END_OF_CHAIN;
        }

        private DirectoryEntry readDirectoryEntry(byte[] data, int offset) {
            int nameLength = unsignedShort(data, offset + 64);
            int type = data[offset + 66] & 0xff;
            if (type == 0) {
                return null;
            }
            if (nameLength < 2 || nameLength > 64 || nameLength % 2 != 0) {
                throw new IllegalArgumentException("CFB 目录名称无效");
            }
            String name = new String(data, offset, nameLength - 2, StandardCharsets.UTF_16LE);
            int startSector = intAt(data, offset + 116);
            long size = longAt(data, offset + 120);
            if (size < 0) {
                throw new IllegalArgumentException("CFB 流大小无效");
            }
            return new DirectoryEntry(name, type, startSector, size);
        }

        private int fatNext(int sector) {
            return sector >= 0 && sector < fat.length ? fat[sector] : FREE_SECTOR;
        }

        private byte[] readSector(int sector) throws IOException {
            requireSector(sector);
            byte[] data = new byte[sectorSize];
            input.seek((long) (sector + 1) * sectorSize);
            input.readFully(data);
            return data;
        }

        private boolean isSector(int sector) {
            return sector >= 0 && sector < totalSectors;
        }

        private void requireSector(int sector) {
            if (!isSector(sector)) {
                throw new IllegalArgumentException("CFB 扇区编号无效");
            }
        }

        private static boolean matches(byte[] actual, byte[] expected) {
            if (actual.length < expected.length) {
                return false;
            }
            for (int index = 0; index < expected.length; index++) {
                if (actual[index] != expected[index]) {
                    return false;
                }
            }
            return true;
        }

        private static int unsignedShort(byte[] data, int offset) {
            return ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xffff;
        }

        private static int intAt(byte[] data, int offset) {
            return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        }

        private static long longAt(byte[] data, int offset) {
            return ByteBuffer.wrap(data, offset, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
        }

        private static final class DirectoryEntry {
            private final String name;
            private final int type;
            private final int startSector;
            private final long size;

            private DirectoryEntry(String name, int type, int startSector, long size) {
                this.name = name;
                this.type = type;
                this.startSector = startSector;
                this.size = size;
            }
        }
    }
}
