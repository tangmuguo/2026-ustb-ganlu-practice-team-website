package com.vihu.ganlu.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "material.memory.test", matches = "true")
class MaterialFileValidatorMemoryPressureTests {
    private static final long PPT_TARGET_SIZE = MaterialFileValidator.MAX_MATERIAL_SIZE - 512L;
    private static final long PPTX_FILLER_SIZE = 190L * 1024L * 1024L;

    @TempDir
    Path tempDirectory;

    private final MaterialFileValidator validator = new MaterialFileValidator();

    @Test
    void validatesNearLimitLegacyPresentationWithRestrictedHeap() throws Exception {
        Path file = tempDirectory.resolve("near-limit.ppt");
        copyFixture("libreoffice-fixture.ppt", file);
        try (RandomAccessFile output = new RandomAccessFile(file.toFile(), "rw")) {
            output.setLength(PPT_TARGET_SIZE);
        }

        assertDoesNotThrow(() -> validator.validate(file, file.getFileName().toString(),
                "MATERIAL", Files.size(file)));
    }

    @Test
    void validatesNearLimitOpenXmlPresentationWithRestrictedHeap() throws Exception {
        Path source = tempDirectory.resolve("source.pptx");
        Path file = tempDirectory.resolve("near-limit.pptx");
        copyFixture("libreoffice-fixture.pptx", source);
        addLargeUnreferencedPart(source, file);
        assertTrue(Files.size(file) > 180L * 1024L * 1024L);

        assertDoesNotThrow(() -> validator.validate(file, file.getFileName().toString(),
                "MATERIAL", Files.size(file)));
    }

    private void copyFixture(String name, Path target) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/material-files/" + name)) {
            if (input == null) {
                throw new IllegalStateException("Missing test fixture: " + name);
            }
            Files.copy(input, target);
        }
    }

    private void addLargeUnreferencedPart(Path source, Path target) throws Exception {
        byte[] buffer = new byte[8192];
        CRC32 checksum = new CRC32();
        for (long remaining = PPTX_FILLER_SIZE; remaining > 0; remaining -= buffer.length) {
            checksum.update(buffer, 0, (int) Math.min(buffer.length, remaining));
        }

        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(source));
             OutputStream fileOutput = Files.newOutputStream(target);
             ZipOutputStream output = new ZipOutputStream(fileOutput)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                output.putNextEntry(new ZipEntry(entry.getName()));
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (count > 0) {
                        output.write(buffer, 0, count);
                    }
                }
                output.closeEntry();
                input.closeEntry();
            }

            ZipEntry filler = new ZipEntry("ppt/unused-large.xml");
            filler.setMethod(ZipEntry.STORED);
            filler.setSize(PPTX_FILLER_SIZE);
            filler.setCompressedSize(PPTX_FILLER_SIZE);
            filler.setCrc(checksum.getValue());
            output.putNextEntry(filler);
            java.util.Arrays.fill(buffer, (byte) 0);
            for (long remaining = PPTX_FILLER_SIZE; remaining > 0; remaining -= buffer.length) {
                output.write(buffer, 0, (int) Math.min(buffer.length, remaining));
            }
            output.closeEntry();
        }
    }
}
