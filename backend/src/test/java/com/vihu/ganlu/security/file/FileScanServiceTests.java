package com.vihu.ganlu.security.file;

import com.vihu.ganlu.mappers.FileSecurityScanMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileScanServiceTests {
    @TempDir
    Path root;

    @Test
    void cleanVerdictIsPublishableAndCanBeRequired() throws Exception {
        Path file = Files.write(root.resolve("clean.bin"), new byte[]{1, 2, 3});
        FileScanService service = new FileScanService(path -> MalwareScanner.ScanVerdict.CLEAN, 1000);

        FileScanResult result = service.scan(file, "TEST", 7);

        assertEquals(FileSecurityStatus.CLEAN, result.getStatus());
        assertEquals(MalwareScanner.ScanVerdict.CLEAN, result.getDiagnosticVerdict());
        assertTrue(service.isClean(file));
        service.requireClean(file);
    }

    @Test
    void infectedVerdictNeverBecomesPublic() throws Exception {
        Path file = Files.write(root.resolve("infected.bin"), new byte[]{4});
        FileScanService service = new FileScanService(path -> MalwareScanner.ScanVerdict.INFECTED, 1000);

        FileScanResult result = service.scan(file, "TEST", 7);

        assertEquals(FileSecurityStatus.INFECTED, result.getStatus());
        assertFalse(result.isClean());
        assertThrows(FileSecurityException.class, () -> service.requireClean(file));
    }

    @Test
    void timeoutFailureAndUnavailableStayPending() throws Exception {
        Path file = Files.write(root.resolve("pending.bin"), new byte[]{5});
        FileScanService timeout = new FileScanService(path -> {
            Thread.sleep(200);
            return MalwareScanner.ScanVerdict.CLEAN;
        }, 20);
        FileScanService failed = new FileScanService(path -> {
            throw new IllegalStateException("scanner failed");
        }, 1000);
        FileScanService unavailable = new FileScanService(null, 1000);

        assertPending(timeout.scan(file, "TEST", 7), MalwareScanner.ScanVerdict.TIMEOUT);
        assertPending(failed.scan(file, "TEST", 7), MalwareScanner.ScanVerdict.FAILED);
        assertPending(unavailable.scan(file, "TEST", 7), MalwareScanner.ScanVerdict.UNAVAILABLE);
    }

    @Test
    void cleanResultWithUnavailableScanLedgerIsDowngradedToPending() throws Exception {
        Path file = Files.write(root.resolve("ledger-failure.bin"), new byte[]{6});
        FileSecurityScanMapper mapper = mock(FileSecurityScanMapper.class);
        when(mapper.upsert(any())).thenReturn(0);
        FileScanService service = new FileScanService(
                path -> MalwareScanner.ScanVerdict.CLEAN, mapper, null, 1000);

        FileScanResult result = service.scan(file, "TEST", 7);

        assertEquals(FileSecurityStatus.PENDING, result.getStatus());
        assertEquals(MalwareScanner.ScanVerdict.CLEAN, result.getDiagnosticVerdict());
        assertFalse(service.isClean(file));
    }

    private void assertPending(FileScanResult result, MalwareScanner.ScanVerdict diagnostic) {
        assertEquals(FileSecurityStatus.PENDING, result.getStatus());
        assertEquals(diagnostic, result.getDiagnosticVerdict());
        assertFalse(result.isClean());
    }
}
