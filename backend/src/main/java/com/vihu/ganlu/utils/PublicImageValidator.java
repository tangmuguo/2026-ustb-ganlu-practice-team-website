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
import java.nio.charset.StandardCharsets;
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
            throw new IllegalArgumentException("仅支持 JPG、JPEG、PNG 和 WebP 位图");
        }

        String declaredType = canonicalMimeType(file.getContentType());
        if (declaredType == null || !declaredType.equals(mimeTypeFor(extension))) {
            throw new IllegalArgumentException("图片扩展名与 Content-Type 不一致");
        }

        String decodedExtension = detectAndValidateContent(file);
        if (!extension.equals(decodedExtension)) {
            throw new IllegalArgumentException("图片扩展名与实际内容不一致");
        }
        return new ValidatedImage(extension, mimeTypeFor(extension));
    }

    private String detectAndValidateContent(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            if (looksLikeWebp(bytes)) {
                validateWebp(bytes);
                return "webp";
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("图片内容损坏或格式不受支持", ex);
        }
        return decodeWithImageIo(file);
    }

    private String decodeWithImageIo(MultipartFile file) {
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
                if (decodedExtension == null || "webp".equals(decodedExtension)) {
                    throw new IllegalArgumentException("仅支持 JPG、JPEG、PNG 和 WebP 位图");
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

    /**
     * Validates the RIFF container, every chunk boundary and WebP dimensions.
     * Java 8 has no built-in WebP decoder, so this follows the WebP container
     * signatures instead of trusting a filename or a browser-supplied MIME type.
     */
    private void validateWebp(byte[] bytes) {
        long riffSize = littleEndianUnsigned(bytes, 4, 4);
        if (riffSize + 8 != bytes.length) {
            throw new IllegalArgumentException("WebP 文件长度与 RIFF 声明不一致");
        }

        boolean hasDimensions = false;
        boolean hasImagePayload = false;
        int offset = 12;
        while (offset < bytes.length) {
            if (bytes.length - offset < 8) {
                throw new IllegalArgumentException("WebP 块头不完整");
            }
            String chunkType = new String(bytes, offset, 4, StandardCharsets.US_ASCII);
            long chunkSize = littleEndianUnsigned(bytes, offset + 4, 4);
            long nextOffset = (long) offset + 8 + chunkSize + (chunkSize & 1L);
            if (chunkSize > Integer.MAX_VALUE || nextOffset > bytes.length) {
                throw new IllegalArgumentException("WebP 块长度不正确");
            }

            int payloadOffset = offset + 8;
            int payloadSize = (int) chunkSize;
            if ("VP8 ".equals(chunkType)) {
                requireChunkSize(payloadSize, 10);
                if ((bytes[payloadOffset + 3] & 0xff) != 0x9d
                        || (bytes[payloadOffset + 4] & 0xff) != 0x01
                        || (bytes[payloadOffset + 5] & 0xff) != 0x2a) {
                    throw new IllegalArgumentException("WebP VP8 帧头不正确");
                }
                int width = (int) littleEndianUnsigned(bytes, payloadOffset + 6, 2) & 0x3fff;
                int height = (int) littleEndianUnsigned(bytes, payloadOffset + 8, 2) & 0x3fff;
                validateDimensions(width, height);
                hasDimensions = true;
                hasImagePayload = true;
            } else if ("VP8L".equals(chunkType)) {
                requireChunkSize(payloadSize, 5);
                if ((bytes[payloadOffset] & 0xff) != 0x2f) {
                    throw new IllegalArgumentException("WebP VP8L 帧头不正确");
                }
                int b1 = bytes[payloadOffset + 1] & 0xff;
                int b2 = bytes[payloadOffset + 2] & 0xff;
                int b3 = bytes[payloadOffset + 3] & 0xff;
                int b4 = bytes[payloadOffset + 4] & 0xff;
                int width = 1 + b1 + ((b2 & 0x3f) << 8);
                int height = 1 + ((b2 & 0xc0) >> 6) + (b3 << 2) + ((b4 & 0x0f) << 10);
                validateDimensions(width, height);
                hasDimensions = true;
                hasImagePayload = true;
            } else if ("VP8X".equals(chunkType)) {
                requireChunkSize(payloadSize, 10);
                int width = 1 + (int) littleEndianUnsigned(bytes, payloadOffset + 4, 3);
                int height = 1 + (int) littleEndianUnsigned(bytes, payloadOffset + 7, 3);
                validateDimensions(width, height);
                hasDimensions = true;
            } else if ("ANMF".equals(chunkType)) {
                requireChunkSize(payloadSize, 16);
                hasImagePayload = true;
            }
            offset = (int) nextOffset;
        }

        if (offset != bytes.length || !hasDimensions || !hasImagePayload) {
            throw new IllegalArgumentException("WebP 缺少有效图像帧");
        }
    }

    private void requireChunkSize(int actual, int minimum) {
        if (actual < minimum) throw new IllegalArgumentException("WebP 图像块不完整");
    }

    private void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height > maxPixels) {
            throw new IllegalArgumentException("图片像素尺寸不正确或过大");
        }
    }

    private boolean looksLikeWebp(byte[] bytes) {
        return bytes.length >= 20
                && asciiEquals(bytes, 0, "RIFF")
                && asciiEquals(bytes, 8, "WEBP");
    }

    private boolean asciiEquals(byte[] bytes, int offset, String expected) {
        if (offset < 0 || bytes.length - offset < expected.length()) return false;
        for (int i = 0; i < expected.length(); i++) {
            if ((byte) expected.charAt(i) != bytes[offset + i]) return false;
        }
        return true;
    }

    private long littleEndianUnsigned(byte[] bytes, int offset, int length) {
        if (offset < 0 || length <= 0 || length > 4 || bytes.length - offset < length) {
            throw new IllegalArgumentException("WebP 文件头不完整");
        }
        long value = 0;
        for (int i = 0; i < length; i++) {
            value |= (long) (bytes[offset + i] & 0xff) << (8 * i);
        }
        return value;
    }

    private String canonicalExtension(String value) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("jpg".equals(normalized) || "jpeg".equals(normalized)) return "jpg";
        if ("png".equals(normalized)) return "png";
        if ("webp".equals(normalized)) return "webp";
        return null;
    }

    private String canonicalMimeType(String value) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if ("image/jpeg".equals(normalized) || "image/jpg".equals(normalized)) return "image/jpeg";
        if ("image/png".equals(normalized)) return "image/png";
        if ("image/webp".equals(normalized)) return "image/webp";
        return null;
    }

    private String mimeTypeFor(String extension) {
        if ("png".equals(extension)) return "image/png";
        if ("webp".equals(extension)) return "image/webp";
        return "image/jpeg";
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
