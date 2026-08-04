package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.FileDeletionTaskEntity;
import com.vihu.ganlu.entitys.PublicImageAssetEntity;
import com.vihu.ganlu.entitys.TeamMediaEntity;
import com.vihu.ganlu.entitys.TeamPageImageEntity;
import com.vihu.ganlu.entitys.TeamPageWordEntity;
import com.vihu.ganlu.mappers.FileDeletionTaskMapper;
import com.vihu.ganlu.mappers.PublicImageQuotaMapper;
import com.vihu.ganlu.mappers.TeamMediaMapper;
import com.vihu.ganlu.mappers.TeamMediaQuotaMapper;
import com.vihu.ganlu.mappers.TeamPageImageMapper;
import com.vihu.ganlu.mappers.TeamPageWordMapper;
import com.vihu.ganlu.service.impl.FileDeletionTaskFailureService;
import com.vihu.ganlu.service.impl.FileDeletionTaskProcessor;
import com.vihu.ganlu.service.impl.FileDeletionTaskService;
import com.vihu.ganlu.service.impl.PublicImageAssetDeletionService;
import com.vihu.ganlu.service.impl.TeamMediaCapacityService;
import com.vihu.ganlu.service.impl.TeamMediaServiceImpl;
import com.vihu.ganlu.utils.FileStorageUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TeamMediaLifecycleTests {
    @TempDir
    Path uploadRoot;

    @Test
    void uploadAtomicallyReservesGlobalThenOwnerQuota() {
        TeamMediaMapper mediaMapper = mock(TeamMediaMapper.class);
        TeamMediaQuotaMapper quota = mock(TeamMediaQuotaMapper.class);
        FileStorageUtil storage = mock(FileStorageUtil.class);
        TeamMediaCapacityService capacity = mock(TeamMediaCapacityService.class);
        FileDeletionTaskService tasks = mock(FileDeletionTaskService.class);
        MockMultipartFile file = new MockMultipartFile("file", "lesson.pdf", "application/pdf", "%PDF-1.7".getBytes());
        FileStorageUtil.ValidatedFile validated = validated(file, "pdf");
        when(storage.validate(file, FileStorageUtil.MAX_VIDEO_SIZE)).thenReturn(validated);
        when(quota.ensureGlobalQuotaRow()).thenReturn(1);
        when(quota.reserveGlobalQuota(anyLong(), anyInt(), anyLong())).thenReturn(1);
        when(quota.ensureOwnerQuotaRow(7)).thenReturn(1);
        when(quota.reserveOwnerQuota(eq(7), anyLong(), anyInt(), anyLong())).thenReturn(1);
        when(storage.storeFile(file, "media/7", "pdf")).thenReturn("media/7/a.pdf");
        when(mediaMapper.insertTeamMedia(any())).thenAnswer(invocation -> {
            ((TeamMediaEntity) invocation.getArgument(0)).setId(11);
            return 1;
        });
        TeamMediaServiceImpl service = service(mediaMapper, quota, storage, capacity, tasks);

        TeamMediaEntity saved = service.uploadMedia(file, 7, 9, null, null);

        assertEquals("media/7/a.pdf", saved.getRelativePath());
        org.mockito.InOrder order = inOrder(quota, storage, mediaMapper);
        order.verify(quota).ensureGlobalQuotaRow();
        order.verify(quota).reserveGlobalQuota(anyLong(), anyInt(), anyLong());
        order.verify(quota).ensureOwnerQuotaRow(7);
        order.verify(quota).reserveOwnerQuota(eq(7), anyLong(), anyInt(), anyLong());
        order.verify(storage).storeFile(file, "media/7", "pdf");
        order.verify(mediaMapper).insertTeamMedia(any());
    }

    @Test
    void globalQuotaRefusalStopsBeforeDiskWrite() {
        TeamMediaMapper mediaMapper = mock(TeamMediaMapper.class);
        TeamMediaQuotaMapper quota = mock(TeamMediaQuotaMapper.class);
        FileStorageUtil storage = mock(FileStorageUtil.class);
        TeamMediaCapacityService capacity = mock(TeamMediaCapacityService.class);
        MockMultipartFile file = new MockMultipartFile("file", "x.pdf", "application/pdf", "%PDF-1.7".getBytes());
        when(storage.validate(eq(file), anyLong())).thenReturn(validated(file, "pdf"));
        when(quota.reserveGlobalQuota(anyLong(), anyInt(), anyLong())).thenReturn(0);

        TeamMediaServiceImpl service = service(mediaMapper, quota, storage, capacity, mock(FileDeletionTaskService.class));
        assertThrows(IllegalStateException.class, () -> service.uploadMedia(file, 7, 9, null, null));
        verify(storage, never()).storeFile(any(), anyString(), anyString());
        verify(quota, never()).reserveOwnerQuota(anyInt(), anyLong(), anyInt(), anyLong());
    }

    @Test
    void archiveDoesNotReleaseQuotaAndPurgeCreatesDurableTask() {
        TeamMediaMapper mediaMapper = mock(TeamMediaMapper.class);
        TeamMediaQuotaMapper quota = mock(TeamMediaQuotaMapper.class);
        FileDeletionTaskService tasks = mock(FileDeletionTaskService.class);
        TeamMediaEntity media = media(12, "ARCHIVED", "media/7/a.pdf");
        when(mediaMapper.findByIdForUpdate(12)).thenReturn(media);
        TeamMediaServiceImpl service = service(mediaMapper, quota, mock(FileStorageUtil.class),
                mock(TeamMediaCapacityService.class), tasks);

        assertTrue(service.purgeById(12));
        verify(tasks).enqueueTeamMedia(media);
        verifyNoInteractions(quota);
        verify(mediaMapper, never()).purgeById(12);
    }

    @Test
    void physicalDeleteFailureKeepsMediaRecordQuotaAndTaskForRetry() throws Exception {
        Path file = uploadRoot.resolve("media/7/a.pdf");
        Files.createDirectories(file.getParent());
        Files.write(file, "%PDF".getBytes());
        FileStorageUtil storage = spy(new FileStorageUtil(uploadRoot.toString(), "test"));
        doThrow(new FileStorageUtil.StorageException("busy")).when(storage).deleteFile("media/7/a.pdf");
        FileDeletionTaskMapper taskMapper = mock(FileDeletionTaskMapper.class);
        TeamMediaMapper mediaMapper = mock(TeamMediaMapper.class);
        TeamMediaQuotaMapper quota = mock(TeamMediaQuotaMapper.class);
        FileDeletionTaskEntity task = task(5, FileDeletionTaskProcessor.TEAM_MEDIA, 12, "media/7/a.pdf");
        when(taskMapper.findByIdForUpdate(5)).thenReturn(task);
        when(mediaMapper.findByIdForUpdate(12)).thenReturn(media(12, "ARCHIVED", "media/7/a.pdf"));
        FileDeletionTaskProcessor processor = processor(taskMapper, mediaMapper, quota, storage, mock(PublicImageQuotaMapper.class));

        assertThrows(FileStorageUtil.StorageException.class, () -> processor.process(5));
        assertTrue(Files.exists(file));
        verify(mediaMapper, never()).purgeById(12);
        verifyNoInteractions(quota);
        verify(taskMapper, never()).deleteTask(5);
    }

    @Test
    void absentPhysicalImageIsIdempotentAndDatabaseStateConverges() {
        FileStorageUtil storage = new FileStorageUtil(uploadRoot.toString(), "test");
        FileDeletionTaskMapper taskMapper = mock(FileDeletionTaskMapper.class);
        PublicImageQuotaMapper imageQuota = mock(PublicImageQuotaMapper.class);
        PublicImageAssetEntity asset = new PublicImageAssetEntity();
        asset.setAssetId(21L);
        asset.setRelativePath("images/7/missing.jpg");
        asset.setOwnerUserId(7);
        asset.setFileSize(99L);
        FileDeletionTaskEntity task = task(6, FileDeletionTaskProcessor.PUBLIC_IMAGE, 21, asset.getRelativePath());
        when(taskMapper.findByIdForUpdate(6)).thenReturn(task);
        when(taskMapper.deleteTask(6)).thenReturn(1);
        when(imageQuota.findAssetByIdForUpdate(21)).thenReturn(asset);
        when(imageQuota.deleteAsset(21)).thenReturn(1);
        when(imageQuota.releasePermanentQuota(7, 99)).thenReturn(1);
        FileDeletionTaskProcessor processor = processor(taskMapper, mock(TeamMediaMapper.class),
                mock(TeamMediaQuotaMapper.class), storage, imageQuota);

        assertTrue(processor.process(6));
        verify(imageQuota).deleteAsset(21);
        verify(imageQuota).releasePermanentQuota(7, 99);
        verify(taskMapper).deleteTask(6);
    }

    @Test
    void firstRetryFailureIsRecordedAndSecondAttemptCanComplete() {
        FileDeletionTaskMapper mapper = mock(FileDeletionTaskMapper.class);
        PublicImageQuotaMapper imageQuota = mock(PublicImageQuotaMapper.class);
        FileDeletionTaskProcessor processor = mock(FileDeletionTaskProcessor.class);
        FileDeletionTaskFailureService failures = mock(FileDeletionTaskFailureService.class);
        FileDeletionTaskEntity task = task(7, FileDeletionTaskProcessor.PUBLIC_IMAGE, 30, "images/7/a.jpg");
        task.setRetryCount(0);
        when(mapper.findById(7)).thenReturn(task);
        when(processor.process(7)).thenThrow(new IllegalStateException("temporary")).thenReturn(true);
        FileDeletionTaskService service = new FileDeletionTaskService(mapper, imageQuota, processor, failures);

        assertFalse(service.retryNow(7));
        verify(failures).recordFailure(7, "temporary", 0);
        assertTrue(service.retryNow(7));
    }

    @Test
    void imageParentIsLockedBeforeQuotaAndAttachmentInsert() {
        TeamMediaMapper mediaMapper = mock(TeamMediaMapper.class);
        TeamMediaQuotaMapper quota = mock(TeamMediaQuotaMapper.class);
        TeamPageImageMapper images = mock(TeamPageImageMapper.class);
        TeamPageWordMapper words = mock(TeamPageWordMapper.class);
        FileStorageUtil storage = mock(FileStorageUtil.class);
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.pdf", "application/pdf", "%PDF-1.7".getBytes());
        when(storage.validate(file, FileStorageUtil.MAX_VIDEO_SIZE)).thenReturn(validated(file, "pdf"));
        TeamPageImageEntity parent = new TeamPageImageEntity();
        parent.setId(31);
        parent.setTeamId(9);
        parent.setStatus("PUBLISHED");
        when(images.findByIdForUpdate(31)).thenReturn(parent);
        when(quota.reserveGlobalQuota(anyLong(), anyInt(), anyLong())).thenReturn(1);
        when(quota.reserveOwnerQuota(eq(7), anyLong(), anyInt(), anyLong())).thenReturn(1);
        when(storage.storeFile(file, "media/7", "pdf")).thenReturn("media/7/a.pdf");
        when(mediaMapper.insertTeamMedia(any())).thenReturn(1);
        TeamMediaServiceImpl service = new TeamMediaServiceImpl(
                mediaMapper, quota, images, words, storage,
                mock(TeamMediaCapacityService.class), mock(FileDeletionTaskService.class),
                2, 400, 10, 2000);

        service.uploadMedia(file, 7, 9, "IMAGE", 31);

        org.mockito.InOrder order = inOrder(images, quota, storage, mediaMapper);
        order.verify(images).findByIdForUpdate(31);
        order.verify(quota).ensureGlobalQuotaRow();
        order.verify(quota).reserveGlobalQuota(anyLong(), anyInt(), anyLong());
        order.verify(storage).storeFile(file, "media/7", "pdf");
        order.verify(mediaMapper).insertTeamMedia(any());
        verifyNoInteractions(words);
    }

    @Test
    void archivedWordParentIsRejectedBeforeQuotaOrPermanentDiskWrite() {
        TeamMediaMapper mediaMapper = mock(TeamMediaMapper.class);
        TeamMediaQuotaMapper quota = mock(TeamMediaQuotaMapper.class);
        TeamPageImageMapper images = mock(TeamPageImageMapper.class);
        TeamPageWordMapper words = mock(TeamPageWordMapper.class);
        FileStorageUtil storage = mock(FileStorageUtil.class);
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.pdf", "application/pdf", "%PDF-1.7".getBytes());
        when(storage.validate(file, FileStorageUtil.MAX_VIDEO_SIZE)).thenReturn(validated(file, "pdf"));
        TeamPageWordEntity parent = new TeamPageWordEntity();
        parent.setId(41);
        parent.setTeamId(9);
        parent.setStatus("ARCHIVED");
        when(words.findByIdForUpdate(41)).thenReturn(parent);
        TeamMediaServiceImpl service = new TeamMediaServiceImpl(
                mediaMapper, quota, images, words, storage,
                mock(TeamMediaCapacityService.class), mock(FileDeletionTaskService.class),
                2, 400, 10, 2000);

        assertThrows(IllegalArgumentException.class,
                () -> service.uploadMedia(file, 7, 9, "WORD", 41));

        verify(words).findByIdForUpdate(41);
        verifyNoInteractions(quota, mediaMapper, images);
        verify(storage, never()).storeFile(any(), anyString(), anyString());
    }

    private TeamMediaServiceImpl service(TeamMediaMapper mediaMapper, TeamMediaQuotaMapper quota,
                                         FileStorageUtil storage, TeamMediaCapacityService capacity,
                                         FileDeletionTaskService tasks) {
        return new TeamMediaServiceImpl(mediaMapper, quota,
                mock(TeamPageImageMapper.class), mock(TeamPageWordMapper.class),
                storage, capacity, tasks,
                2, 400, 10, 2000);
    }

    private FileDeletionTaskProcessor processor(FileDeletionTaskMapper tasks, TeamMediaMapper media,
                                                TeamMediaQuotaMapper mediaQuota, FileStorageUtil storage,
                                                PublicImageQuotaMapper imageQuota) {
        return new FileDeletionTaskProcessor(tasks,
                new PublicImageAssetDeletionService(storage, imageQuota), media, mediaQuota, storage);
    }

    private FileStorageUtil.ValidatedFile validated(MockMultipartFile file, String extension) {
        FileStorageUtil.ValidatedFile validated = new FileStorageUtil.ValidatedFile();
        validated.setRaw(file);
        validated.setExtension(extension);
        validated.setMimeType(file.getContentType());
        validated.setSize(file.getSize());
        validated.setCategory(FileStorageUtil.FileCategory.DOCUMENT);
        return validated;
    }

    private TeamMediaEntity media(int id, String status, String path) {
        TeamMediaEntity media = new TeamMediaEntity();
        media.setId(id);
        media.setStatus(status);
        media.setRelativePath(path);
        media.setUploaderId(7);
        media.setFileSize(4L);
        return media;
    }

    private FileDeletionTaskEntity task(long id, String type, long assetId, String path) {
        FileDeletionTaskEntity task = new FileDeletionTaskEntity();
        task.setId(id);
        task.setAssetType(type);
        task.setAssetId(assetId);
        task.setRelativePath(path);
        task.setOwnerUserId(7);
        task.setFileSize(4L);
        task.setRetryCount(0);
        return task;
    }
}
