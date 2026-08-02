package com.vihu.ganlu.utils;

import com.vihu.ganlu.entitys.UploadedFileInfo;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

        String mimeType = validateContent(file, extension);
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

    private String validateContent(Path file, String extension) throws IOException {
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
                require(isReadableLegacyPowerPoint(file));
                return "application/vnd.ms-powerpoint";
            case "pptx":
                require(isReadableOpenXmlPowerPoint(file));
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "jpg":
            case "jpeg":
                require((header[0] & 0xff) == 0xff
                        && (header[1] & 0xff) == 0xd8
                        && (header[2] & 0xff) == 0xff);
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

    private boolean isReadableLegacyPowerPoint(Path file) {
        try (InputStream input = Files.newInputStream(file);
             HSLFSlideShow slideShow = new HSLFSlideShow(input)) {
            List<HSLFSlide> slides = slideShow.getSlides();
            if (slides.isEmpty()) {
                return false;
            }
            for (HSLFSlide slide : slides) {
                // Accessing shapes forces POI to resolve the PowerPoint records used by the slide.
                slide.getShapes().size();
            }
            return true;
        } catch (Exception invalidPresentation) {
            return false;
        }
    }

    private boolean isReadableOpenXmlPowerPoint(Path file) {
        try (InputStream input = Files.newInputStream(file);
             XMLSlideShow slideShow = new XMLSlideShow(input)) {
            List<XSLFSlide> slides = slideShow.getSlides();
            if (slides.isEmpty()) {
                return false;
            }
            for (XSLFSlide slide : slides) {
                // This resolves slide XML, relationships and related parts rather than trusting ZIP names.
                slide.getShapes().size();
                slide.getRelations().size();
            }
            return true;
        } catch (Exception invalidPresentation) {
            return false;
        }
    }

    private boolean startsWith(byte[] actual, int actualLength, byte[] expected) {
        if (actualLength < expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (actual[index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new IllegalArgumentException("文件扩展名与实际内容不匹配或演示文稿无法解析");
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
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("当前 Java 环境不支持 MD5", error);
        }
    }
}
