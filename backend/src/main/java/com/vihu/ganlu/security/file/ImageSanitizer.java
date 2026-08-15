package com.vihu.ganlu.security.file;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;

import org.springframework.stereotype.Component;

/**
 * Server-side image normalizer. ImageIO decodes pixels and writes a fresh
 * image without carrying source metadata, including EXIF/GPS blocks.
 */
@Component
public class ImageSanitizer {
    private final boolean bypass;

    public ImageSanitizer() {
        this(false);
    }

    private ImageSanitizer(boolean bypass) {
        this.bypass = bypass;
    }

    /** Compatibility helper for legacy isolated unit tests only. */
    public static ImageSanitizer passthroughForTests() {
        return new ImageSanitizer(true);
    }

    /** Rewrite {@code source} into {@code target}; target must not be public yet. */
    public void sanitize(Path source, Path target, String extension) {
        if (source == null || target == null || !Files.isRegularFile(source)) {
            throw new FileSecurityException("图片隔离文件不存在");
        }
        if (bypass) {
            try {
                if (!source.equals(target)) Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException error) {
                throw new FileSecurityException("图片测试副本失败");
            }
        }

        String format = normalizeFormat(extension);
        if ("webp".equals(format)) {
            // JDK 8 ImageIO has no trusted WebP writer. Do not publish a WebP
            // based solely on its original bytes; reject it until a vetted
            // decoder/encoder is supplied by the deployment.
            throw new FileSecurityException("服务器暂不支持安全重编码 WebP 图片");
        }
        try {
            BufferedImage decoded = ImageIO.read(source.toFile());
            if (decoded == null) throw new IOException("无法解码图片");
            BufferedImage normalized = decoded;
            if ("jpg".equals(format) || "jpeg".equals(format)) {
                normalized = new BufferedImage(decoded.getWidth(), decoded.getHeight(),
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = normalized.createGraphics();
                try {
                    graphics.setColor(java.awt.Color.WHITE);
                    graphics.fillRect(0, 0, normalized.getWidth(), normalized.getHeight());
                    graphics.drawImage(decoded, 0, 0, null);
                } finally {
                    graphics.dispose();
                }
            }
            Files.createDirectories(target.toAbsolutePath().normalize().getParent());
            write(normalized, target, format);
        } catch (IOException error) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) { }
            throw new FileSecurityException("图片无法安全重编码");
        }
    }

    /** Rewrite a staged file in place, using a sibling temporary file. */
    public void sanitizeInPlace(Path source, String extension) {
        if (bypass) return;
        String normalized = normalizeFormat(extension);
        Path temporary = source.resolveSibling(".sanitized-" + java.util.UUID.randomUUID()
                + "." + normalized);
        try {
            sanitize(source, temporary, normalized);
            Files.move(temporary, source, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException error) {
            try {
                Files.move(temporary, source, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveError) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
                throw new FileSecurityException("图片安全重编码替换失败");
            }
        } catch (IOException error) {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            throw new FileSecurityException("图片安全重编码替换失败");
        }
    }

    private void write(BufferedImage image, Path target, String format) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) throw new IOException("没有安全图片编码器: " + format);
        ImageWriter writer = writers.next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(target.toFile())) {
            writer.setOutput(output);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (("jpg".equals(format) || "jpeg".equals(format))
                    && params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(0.92f);
            }
            // IIOImage carries no source IIOMetadata, so EXIF/XMP/GPS blocks
            // are not copied to the new file.
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
    }

    private String normalizeFormat(String extension) {
        String normalized = extension == null ? "" : extension.trim().toLowerCase(java.util.Locale.ROOT);
        if ("jpeg".equals(normalized)) return "jpg";
        if (!"jpg".equals(normalized) && !"png".equals(normalized) && !"webp".equals(normalized)) {
            throw new FileSecurityException("图片扩展名不支持安全重编码");
        }
        return normalized;
    }
}
