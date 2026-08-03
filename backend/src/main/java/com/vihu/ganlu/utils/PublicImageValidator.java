package com.vihu.ganlu.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;

/** Validates files before they can enter the same-origin public image directory. */
@Component
public class PublicImageValidator {
    private final long maxBytes;
    private final long maxPixels;

    public PublicImageValidator(
            @Value("${team.public-image.max-bytes:5242880}") long maxBytes,
            @Value("${team.public-image.max-pixels:20000000}") long maxPixels) {
        if (maxBytes <= 0 || maxPixels <= 0) {
            throw new IllegalArgumentException("公开图片大小和像素限制必须大于0");
        }
        this.maxBytes = maxBytes;
        this.maxPixels = maxPixels;
    }

    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传图片不能为空");
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("图片大小不能超过" + (maxBytes / 1024 / 1024) + "MB");
        }

        String extension = canonicalExtension(FileStorageUtil.extensionOf(file.getOriginalFilename()));
        if (extension == null) {
            throw new IllegalArgumentException("仅支持 JPG、JPEG 和 PNG 位图");
        }

        String declaredType = canonicalMimeType(file.getContentType());
        if (declaredType == null || !declaredType.equals(mimeTypeFor(extension))) {
            throw new IllegalArgumentException("图片扩展名与 Content-Type 不一致");
        }

        String decodedExtension = decodeAndDetect(file);
        if (!extension.equals(decodedExtension)) {
            throw new IllegalArgumentException("图片扩展名与实际内容不一致");
        }
        return new ValidatedImage(extension, mimeTypeFor(extension));
    }

    private String decodeAndDetect(MultipartFile file) {
        try (InputStream input = file.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) {
                throw new IllegalArgumentException("图片内容损坏或格式不受支持");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("图片内容损坏或格式不受支持");
            }

            ImageReader reader = readers.next();
            try {
                String decodedExtension = canonicalExtension(reader.getFormatName());
                if (decodedExtension == null) {
                    throw new IllegalArgumentException("仅支持 JPG、JPEG 和 PNG 位图");
                }
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > maxPixels) {
                    throw new IllegalArgumentException("图片像素尺寸不正确或过大");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw new IllegalArgumentException("图片内容损坏或格式不受支持");
                }
                return decodedExtension;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) ex;
            }
            throw new IllegalArgumentException("图片内容损坏或格式不受支持", ex);
        }
    }

    private String canonicalExtension(String value) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("jpg".equals(normalized) || "jpeg".equals(normalized)) return "jpg";
        if ("png".equals(normalized)) return "png";
        return null;
    }

    private String canonicalMimeType(String value) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if ("image/jpeg".equals(normalized) || "image/jpg".equals(normalized)) return "image/jpeg";
        if ("image/png".equals(normalized)) return "image/png";
        return null;
    }

    private String mimeTypeFor(String extension) {
        return "png".equals(extension) ? "image/png" : "image/jpeg";
    }

    public static final class ValidatedImage {
        private final String extension;
        private final String contentType;

        public ValidatedImage(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        public String getExtension() { return extension; }
        public String getContentType() { return contentType; }
    }
}
