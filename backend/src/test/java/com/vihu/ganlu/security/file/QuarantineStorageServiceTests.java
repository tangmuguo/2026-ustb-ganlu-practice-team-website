package com.vihu.ganlu.security.file;

import com.vihu.ganlu.mappers.FileSecurityScanMapper;
import com.vihu.ganlu.utils.FileStorageUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuarantineStorageServiceTests {
    @TempDir
    Path root;

    @Test
    void cleanFileMovesFromQuarantineToControlledRoot() {
        FileStorageUtil storage = new FileStorageUtil(root.toString(), "test");
        QuarantineStorageService service = new QuarantineStorageService(storage,
                new FileScanService(path -> MalwareScanner.ScanVerdict.CLEAN, 1000));
        QuarantinedFile file = service.stage(
                new MockMultipartFile("file", "lesson.pdf", "application/pdf", "%PDF".getBytes()),
                "MATERIAL", 7, "pdf");

        service.scan(file);
        String controlled = service.promoteIfClean(file, "protected/materials");

        assertTrue(controlled.startsWith("protected/materials/7/"));
        assertFalse(Files.exists(root.resolve(file.getQuarantinePath())));
        assertTrue(Files.isRegularFile(root.resolve(controlled)));
        assertTrue(Files.isRegularFile(service.loadControlled(file)));
    }

    @Test
    void scannerFailureKeepsFilePendingAndInQuarantine() {
        FileStorageUtil storage = new FileStorageUtil(root.toString(), "test");
        QuarantineStorageService service = new QuarantineStorageService(storage,
                new FileScanService(path -> MalwareScanner.ScanVerdict.FAILED, 1000));
        QuarantinedFile file = service.stage(
                new MockMultipartFile("file", "lesson.pdf", "application/pdf", "%PDF".getBytes()),
                "MATERIAL", 7, "pdf");

        FileScanResult result = service.scan(file);

        assertTrue(result.getStatus() == FileSecurityStatus.PENDING);
        assertTrue(Files.isRegularFile(root.resolve(file.getQuarantinePath())));
        assertThrows(FileSecurityException.class, () -> service.promoteIfClean(file, "protected/materials"));
    }

    @Test
    void scanLedgerPathFailurePreventsPhysicalMove() throws Exception {
        FileStorageUtil storage = new FileStorageUtil(root.toString(), "test");
        FileSecurityScanMapper mapper = mock(FileSecurityScanMapper.class);
        when(mapper.upsert(any())).thenReturn(1);
        when(mapper.updatePath(any(), any())).thenReturn(0);
        FileScanService scan = new FileScanService(
                path -> MalwareScanner.ScanVerdict.CLEAN, mapper, null, 1000);
        QuarantineStorageService service = new QuarantineStorageService(storage, scan);
        QuarantinedFile file = service.stage(
                new MockMultipartFile("file", "lesson.pdf", "application/pdf", "%PDF".getBytes()),
                "MATERIAL", 7, "pdf");
        service.scan(file);

        assertThrows(FileSecurityException.class, () -> service.promoteIfClean(file, "protected/materials"));
        assertTrue(Files.isRegularFile(root.resolve(file.getQuarantinePath())));
        assertFalse(Files.exists(root.resolve("protected/materials/7"))
                && Files.list(root.resolve("protected/materials/7")).findAny().isPresent());
    }
}
