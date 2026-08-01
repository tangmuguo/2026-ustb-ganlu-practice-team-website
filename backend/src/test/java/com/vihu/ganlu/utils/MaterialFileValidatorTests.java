package com.vihu.ganlu.utils;

import com.vihu.ganlu.entitys.UploadedFileInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MaterialFileValidatorTests {
    @TempDir
    Path tempDirectory;

    private final MaterialFileValidator validator = new MaterialFileValidator();

    @Test
    void acceptsPdfWithMatchingHeader() throws Exception {
        Path file = tempDirectory.resolve("lesson.pdf");
        Files.write(file, "%PDF-1.7\nexample".getBytes(StandardCharsets.US_ASCII));

        UploadedFileInfo info = validator.validate(file, "lesson.pdf", "MATERIAL", Files.size(file));

        assertEquals("pdf", info.getExtension());
        assertEquals("application/pdf", info.getMimeType());
        assertEquals(32, info.getChecksum().length());
    }

    @Test
    void rejectsFileWhoseExtensionDoesNotMatchContent() throws Exception {
        Path file = tempDirectory.resolve("fake.pdf");
        Files.write(file, "not a pdf".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(file, "fake.pdf", "MATERIAL", Files.size(file)));
    }

    @Test
    void rejectsDocAndVideoExtensions() throws Exception {
        Path file = tempDirectory.resolve("lesson.doc");
        Files.write(file, "document".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(file, "lesson.doc", "MATERIAL", Files.size(file)));
    }
}
