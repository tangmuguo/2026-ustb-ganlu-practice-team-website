package com.vihu.ganlu.service;

import com.vihu.ganlu.mappers.TeamMediaQuotaMapper;
import com.vihu.ganlu.service.impl.TeamMediaCapacityService;
import com.vihu.ganlu.utils.FileStorageUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TeamMediaCapacityServiceTests {
    @TempDir
    Path disk;

    @Test
    void sameDeviceIsCheckedOnceAndReservationIsPersistedUnderCoordinatorLock() {
        TeamMediaQuotaMapper mapper = baseMapper();
        FileStorageUtil storage = mock(FileStorageUtil.class);
        when(storage.getUploadRoot()).thenReturn(disk);
        when(storage.getUsableSpace(any(Path.class))).thenReturn(10_000_000L);
        when(mapper.insertUploadReservation(anyString(), eq(7), eq(200L), any(Timestamp.class))).thenReturn(1);
        TeamMediaCapacityService service = service(storage, mapper, 1, 1, 4, 12);

        TeamMediaCapacityService.UploadAdmission admission = service.reserveAdmission(7, 200);

        assertEquals(200L, admission.getReservedBytes());
        org.mockito.InOrder order = inOrder(mapper);
        order.verify(mapper).ensureGlobalQuotaRow();
        order.verify(mapper).lockGlobalQuotaRow();
        order.verify(mapper).cleanupUploadReservations(any());
        order.verify(mapper).countActiveUploadReservations();
        order.verify(mapper).sumActiveUploadReservationBytes();
        order.verify(mapper).countRecentUploadAttempts(eq(7), any());
        order.verify(mapper).insertUploadReservation(anyString(), eq(7), eq(200L), any());
        verify(storage, times(1)).getUsableSpace(any(Path.class));
    }

    @Test
    void existingCrossInstanceReservationsAreIncludedAtomically() {
        TeamMediaQuotaMapper mapper = baseMapper();
        when(mapper.sumActiveUploadReservationBytes()).thenReturn(850L);
        FileStorageUtil storage = mock(FileStorageUtil.class);
        when(storage.getUploadRoot()).thenReturn(disk);
        when(storage.getUsableSpace(any(Path.class))).thenReturn(1_000L);
        TeamMediaCapacityService service = service(storage, mapper, 1, 1, 4, 12);

        TeamMediaCapacityService.UploadAdmissionException error = assertThrows(
                TeamMediaCapacityService.UploadAdmissionException.class,
                () -> service.reserveAdmission(7, 200));

        assertEquals(507, error.getHttpStatus());
        verify(mapper, never()).insertUploadReservation(anyString(), anyInt(), anyLong(), any());
    }

    @Test
    void globalConcurrencyAndPerUserRateAreRejectedBeforeDiskAdmission() {
        TeamMediaQuotaMapper concurrencyMapper = baseMapper();
        when(concurrencyMapper.countActiveUploadReservations()).thenReturn(4);
        TeamMediaCapacityService concurrent = service(mockStorage(), concurrencyMapper, 1, 1, 4, 12);
        assertEquals(429, assertThrows(TeamMediaCapacityService.UploadAdmissionException.class,
                () -> concurrent.reserveAdmission(7, 200)).getHttpStatus());

        TeamMediaQuotaMapper rateMapper = baseMapper();
        when(rateMapper.countRecentUploadAttempts(eq(7), any())).thenReturn(12);
        TeamMediaCapacityService rate = service(mockStorage(), rateMapper, 1, 1, 4, 12);
        assertEquals(429, assertThrows(TeamMediaCapacityService.UploadAdmissionException.class,
                () -> rate.reserveAdmission(7, 200)).getHttpStatus());
    }

    private TeamMediaQuotaMapper baseMapper() {
        TeamMediaQuotaMapper mapper = mock(TeamMediaQuotaMapper.class);
        when(mapper.ensureGlobalQuotaRow()).thenReturn(1);
        when(mapper.lockGlobalQuotaRow()).thenReturn(1);
        when(mapper.countActiveUploadReservations()).thenReturn(0);
        when(mapper.sumActiveUploadReservationBytes()).thenReturn(0L);
        when(mapper.countRecentUploadAttempts(anyInt(), any())).thenReturn(0);
        return mapper;
    }

    private FileStorageUtil mockStorage() {
        FileStorageUtil storage = mock(FileStorageUtil.class);
        when(storage.getUploadRoot()).thenReturn(disk);
        when(storage.getUsableSpace(any(Path.class))).thenReturn(Long.MAX_VALUE);
        return storage;
    }

    private TeamMediaCapacityService service(
            FileStorageUtil storage, TeamMediaQuotaMapper mapper,
            long uploadReserve, long multipartReserve, int concurrent, int rate) {
        return new TeamMediaCapacityService(storage, mapper, disk.toString(),
                uploadReserve, multipartReserve, concurrent, rate, 120);
    }
}
