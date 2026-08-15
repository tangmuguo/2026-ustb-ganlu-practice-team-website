package com.vihu.ganlu.security.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageSanitizerTests {
    @TempDir
    Path root;

    @Test
    void jpegIsDecodedAndExifGpsPayloadIsNotCopied() throws Exception {
        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "jpg", bytes));
        byte[] source = withExifGps(bytes.toByteArray());
        Path original = Files.write(root.resolve("with-exif.jpg"), source);
        Path sanitized = root.resolve("safe.jpg");

        new ImageSanitizer().sanitize(original, sanitized, "jpg");

        assertTrue(Files.size(sanitized) > 0);
        assertTrue(ImageIO.read(sanitized.toFile()) != null);
        byte[] output = Files.readAllBytes(sanitized);
        assertFalse(indexOf(output, "Exif".getBytes(java.nio.charset.StandardCharsets.US_ASCII)) >= 0);
        assertFalse(indexOf(output, "GPS".getBytes(java.nio.charset.StandardCharsets.US_ASCII)) >= 0);
    }

    @Test
    void unsupportedWebpCannotBeCopiedIntoControlledPath() throws Exception {
        Path original = Files.write(root.resolve("image.webp"), new byte[]{'R', 'I', 'F', 'F'});
        assertThrows(FileSecurityException.class,
                () -> new ImageSanitizer().sanitize(original, root.resolve("safe.webp"), "webp"));
    }

    private byte[] withExifGps(byte[] jpeg) {
        byte[] payload = "Exif\0\0GPSLatitude=1;GPSLongitude=2".getBytes(
                java.nio.charset.StandardCharsets.US_ASCII);
        int length = payload.length + 2;
        byte[] segment = new byte[payload.length + 4];
        segment[0] = (byte) 0xFF;
        segment[1] = (byte) 0xE1;
        segment[2] = (byte) ((length >>> 8) & 0xFF);
        segment[3] = (byte) (length & 0xFF);
        System.arraycopy(payload, 0, segment, 4, payload.length);
        byte[] result = new byte[jpeg.length + segment.length];
        result[0] = jpeg[0];
        result[1] = jpeg[1];
        System.arraycopy(segment, 0, result, 2, segment.length);
        System.arraycopy(jpeg, 2, result, 2 + segment.length, jpeg.length - 2);
        return result;
    }

    private int indexOf(byte[] haystack, byte[] needle) {
        outer: for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

}
