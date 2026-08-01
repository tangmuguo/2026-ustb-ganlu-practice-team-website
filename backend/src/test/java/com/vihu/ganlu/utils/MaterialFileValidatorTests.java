package com.vihu.ganlu.utils;

import com.vihu.ganlu.entitys.UploadedFileInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    @Test
    void rejectsLegacyWordDocumentRenamedAsPpt() throws Exception {
        Path file = tempDirectory.resolve("renamed.ppt");
        Files.write(file, compoundDocumentWithStream("WordDocument"));

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(file, "renamed.ppt", "MATERIAL", Files.size(file)));
    }

    @Test
    void acceptsLegacyPowerPointWithReadablePresentationStream() throws Exception {
        Path file = tempDirectory.resolve("lesson.ppt");
        Files.write(file, compoundDocumentWithStream("PowerPoint Document"));

        UploadedFileInfo info = validator.validate(file, "lesson.ppt", "MATERIAL", Files.size(file));

        assertEquals("application/vnd.ms-powerpoint", info.getMimeType());
    }

    @Test
    void rejectsZipRenamedAsPptx() throws Exception {
        Path file = tempDirectory.resolve("fake.pptx");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(file))) {
            addZipEntry(output, "ppt/presentation.xml", "<p:presentation/>");
        }

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(file, "fake.pptx", "MATERIAL", Files.size(file)));
    }

    @Test
    void acceptsPptxWithRequiredPresentationStructure() throws Exception {
        Path file = tempDirectory.resolve("lesson.pptx");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(file))) {
            addZipEntry(output, "[Content_Types].xml",
                    "<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                            + "<Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>"
                            + "</Types>");
            addZipEntry(output, "_rels/.rels",
                    "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"ppt/presentation.xml\"/>"
                            + "</Relationships>");
            addZipEntry(output, "ppt/presentation.xml",
                    "<?xml version=\"1.0\"?><p:presentation xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\"/>");
            addZipEntry(output, "ppt/_rels/presentation.xml.rels",
                    "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"/>");
        }

        UploadedFileInfo info = validator.validate(file, "lesson.pptx", "MATERIAL", Files.size(file));

        assertEquals("application/vnd.openxmlformats-officedocument.presentationml.presentation", info.getMimeType());
    }

    private void addZipEntry(ZipOutputStream output, String name, String content) throws Exception {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private byte[] compoundDocumentWithStream(String streamName) {
        final int sectorSize = 512;
        final int fatSector = 0;
        final int directorySector = 1;
        final int streamStartSector = 2;
        final int streamSectorCount = 8;
        final int endOfChain = -2;
        final int freeSector = -1;
        byte[] file = new byte[sectorSize * (1 + 2 + streamSectorCount)];
        Arrays.fill(file, (byte) 0);

        ByteBuffer header = ByteBuffer.wrap(file, 0, sectorSize).order(ByteOrder.LITTLE_ENDIAN);
        header.put(new byte[]{
                (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
        });
        header.putShort(24, (short) 0x003e);
        header.putShort(26, (short) 3);
        header.putShort(28, (short) 0xfffe);
        header.putShort(30, (short) 9);
        header.putShort(32, (short) 6);
        header.putInt(40, 0);
        header.putInt(44, 1);
        header.putInt(48, directorySector);
        header.putInt(56, 4096);
        header.putInt(60, endOfChain);
        header.putInt(64, 0);
        header.putInt(68, endOfChain);
        header.putInt(72, 0);
        for (int index = 0; index < 109; index++) {
            header.putInt(76 + index * 4, freeSector);
        }
        header.putInt(76, fatSector);

        int fatOffset = sectorSize;
        ByteBuffer fat = ByteBuffer.wrap(file, fatOffset, sectorSize).slice().order(ByteOrder.LITTLE_ENDIAN);
        for (int index = 0; index < sectorSize / 4; index++) {
            fat.putInt(index * 4, freeSector);
        }
        fat.putInt(fatSector * 4, -3);
        fat.putInt(directorySector * 4, endOfChain);
        for (int index = 0; index < streamSectorCount; index++) {
            int sector = streamStartSector + index;
            fat.putInt(sector * 4, index + 1 == streamSectorCount ? endOfChain : sector + 1);
        }

        int directoryOffset = sectorSize * (directorySector + 1);
        writeDirectoryEntry(file, directoryOffset, "Root Entry", 5, endOfChain, 0L);
        writeDirectoryEntry(file, directoryOffset + 128, streamName, 2, streamStartSector, 4096L);
        return file;
    }

    private void writeDirectoryEntry(byte[] file, int offset, String name, int type, int startSector, long size) {
        byte[] encodedName = (name + "\0").getBytes(StandardCharsets.UTF_16LE);
        System.arraycopy(encodedName, 0, file, offset, encodedName.length);
        ByteBuffer entry = ByteBuffer.wrap(file, offset, 128).slice().order(ByteOrder.LITTLE_ENDIAN);
        entry.putShort(64, (short) encodedName.length);
        entry.put(66, (byte) type);
        entry.putInt(116, startSector);
        entry.putLong(120, size);
    }
}
