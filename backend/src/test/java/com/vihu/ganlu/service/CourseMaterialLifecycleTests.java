package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.CourseDetailEntity;
import com.vihu.ganlu.entitys.FileDeletionTaskEntity;
import com.vihu.ganlu.entitys.MaterialCreateRequest;
import com.vihu.ganlu.entitys.UploadedFileInfo;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.mappers.CourseDetailMapper;
import com.vihu.ganlu.mappers.FileDeletionTaskMapper;
import com.vihu.ganlu.mappers.PublicImageQuotaMapper;
import com.vihu.ganlu.mappers.TeamMediaMapper;
import com.vihu.ganlu.mappers.TeamMediaQuotaMapper;
import com.vihu.ganlu.service.impl.CourseDetailServiceImpl;
import com.vihu.ganlu.service.impl.FileDeletionTaskProcessor;
import com.vihu.ganlu.service.impl.FileDeletionTaskFailureService;
import com.vihu.ganlu.service.impl.FileDeletionTaskService;
import com.vihu.ganlu.service.impl.MaterialUploadStorageService;
import com.vihu.ganlu.service.impl.PublicImageAssetDeletionService;
import com.vihu.ganlu.utils.FileStorageUtil;
import com.vihu.ganlu.utils.MaterialFileValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;
import java.util.List;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CourseMaterialLifecycleTests {
    @TempDir
    Path uploadRoot;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteLocksAndWritesDurableTasksWithoutDeletingFilesInsideTransaction() {
        CourseDetailMapper mapper = mock(CourseDetailMapper.class);
        FileDeletionTaskService tasks = mock(FileDeletionTaskService.class);
        FileStorageUtil storage = mock(FileStorageUtil.class);
        CourseDetailEntity course = course(41, 0, "protected/materials/a.pdf");
        when(mapper.getCourseByIdForUpdate(41)).thenReturn(course);
        when(mapper.softDeleteCourseById(41)).thenReturn(1);
        CourseDetailServiceImpl service = service(mapper, storage, mock(MaterialUploadStorageService.class), tasks);

        assertTrue(service.deleteCourseById(41));

        verify(mapper).getCourseByIdForUpdate(41);
        verify(mapper).softDeleteCourseById(41);
        verify(tasks).enqueueCourseFiles(course);
        verifyNoInteractions(storage);
    }

    @Test
    void courseIdAndFileRoleFormThreeStableDeletionIdentities() {
        FileDeletionTaskMapper mapper = mock(FileDeletionTaskMapper.class);
        AtomicLong ids = new AtomicLong(100);
        when(mapper.insertTask(any())).thenAnswer(invocation -> {
            ((FileDeletionTaskEntity) invocation.getArgument(0)).setId(ids.incrementAndGet());
            return 1;
        });
        FileDeletionTaskService service = new FileDeletionTaskService(
                mapper, mock(PublicImageQuotaMapper.class), mock(FileDeletionTaskProcessor.class),
                mock(FileDeletionTaskFailureService.class));
        TransactionSynchronizationManager.initSynchronization();

        service.enqueueCourseFiles(course(55, 0, "protected/materials/55.pdf"));

        org.mockito.ArgumentCaptor<FileDeletionTaskEntity> captor =
                org.mockito.ArgumentCaptor.forClass(FileDeletionTaskEntity.class);
        verify(mapper, times(3)).insertTask(captor.capture());
        assertEquals(new HashSet<>(Arrays.asList(
                FileDeletionTaskProcessor.COURSE_COVER,
                FileDeletionTaskProcessor.COURSE_ORIGINAL,
                FileDeletionTaskProcessor.COURSE_PREVIEW)),
                captor.getAllValues().stream().map(FileDeletionTaskEntity::getAssetType).collect(Collectors.toSet()));
        assertTrue(captor.getAllValues().stream().allMatch(task -> Long.valueOf(55).equals(task.getAssetId())));
    }

    @Test
    void bothSharedCoursesAlwaysLeaveDurableTasksForConcurrentDeletion() {
        CourseDetailMapper mapper = mock(CourseDetailMapper.class);
        FileDeletionTaskService tasks = mock(FileDeletionTaskService.class);
        CourseDetailEntity first = sharedCourse(61);
        CourseDetailEntity second = sharedCourse(62);
        when(mapper.getCourseByIdForUpdate(61)).thenReturn(first);
        when(mapper.getCourseByIdForUpdate(62)).thenReturn(second);
        when(mapper.softDeleteCourseById(anyInt())).thenReturn(1);
        CourseDetailServiceImpl service = service(
                mapper, mock(FileStorageUtil.class), mock(MaterialUploadStorageService.class), tasks);

        assertTrue(service.deleteCourseById(61));
        assertTrue(service.deleteCourseById(62));

        verify(tasks).enqueueCourseFiles(first);
        verify(tasks).enqueueCourseFiles(second);
        verify(mapper, never()).countActiveFileReferences(anyString(), any());
        assertEquals("protected/materials/shared.pdf", first.getOriginalFilePath());
        assertEquals("protected/materials/shared.pdf", second.getOriginalFilePath());
    }

    @Test
    void deletingOneHistoricalSharedCourseKeepsTheOtherCoursesFile() throws Exception {
        String path = "protected/materials/shared.pdf";
        Path file = write(path, "%PDF-shared");
        FileDeletionTaskMapper tasks = mock(FileDeletionTaskMapper.class);
        CourseDetailMapper courses = mock(CourseDetailMapper.class);
        FileDeletionTaskEntity task = task(1, FileDeletionTaskProcessor.COURSE_ORIGINAL, 41, path);
        when(tasks.findByIdForUpdate(1)).thenReturn(task);
        when(tasks.deleteTask(1)).thenReturn(1);
        when(courses.getCourseByIdIncludingDeletedForUpdate(41)).thenReturn(course(41, 0, path));
        when(courses.countActiveFileReferences(path, 41)).thenReturn(1);

        assertTrue(processor(tasks, courses, new FileStorageUtil(uploadRoot.toString(), "test")).process(1));

        assertTrue(Files.exists(file));
        verify(tasks).deleteTask(1);
    }

    @Test
    void unsharedDeletedCourseFileIsRemovedIdempotently() throws Exception {
        String path = "protected/materials/only.pdf";
        Path file = write(path, "%PDF-only");
        FileDeletionTaskMapper tasks = mock(FileDeletionTaskMapper.class);
        CourseDetailMapper courses = mock(CourseDetailMapper.class);
        when(tasks.findByIdForUpdate(2)).thenReturn(task(2, FileDeletionTaskProcessor.COURSE_ORIGINAL, 42, path));
        when(tasks.deleteTask(2)).thenReturn(1);
        when(courses.getCourseByIdIncludingDeletedForUpdate(42)).thenReturn(course(42, 0, path));
        when(courses.countActiveFileReferences(path, 42)).thenReturn(0);

        assertTrue(processor(tasks, courses, new FileStorageUtil(uploadRoot.toString(), "test")).process(2));

        assertFalse(Files.exists(file));
        verify(tasks).deleteTask(2);
    }

    @Test
    void tasksFromBothDeletedSharedCoursesConvergeOnOneFileIdempotently() throws Exception {
        String path = "protected/materials/concurrent-shared.pdf";
        Path file = write(path, "%PDF-concurrent");
        FileDeletionTaskMapper tasks = mock(FileDeletionTaskMapper.class);
        CourseDetailMapper courses = mock(CourseDetailMapper.class);
        when(tasks.findByIdForUpdate(7)).thenReturn(
                task(7, FileDeletionTaskProcessor.COURSE_ORIGINAL, 61, path));
        when(tasks.findByIdForUpdate(8)).thenReturn(
                task(8, FileDeletionTaskProcessor.COURSE_ORIGINAL, 62, path));
        when(tasks.deleteTask(anyLong())).thenReturn(1);
        when(courses.getCourseByIdIncludingDeletedForUpdate(61)).thenReturn(course(61, 0, path));
        when(courses.getCourseByIdIncludingDeletedForUpdate(62)).thenReturn(course(62, 0, path));
        when(courses.countActiveFileReferences(path, 61)).thenReturn(0);
        when(courses.countActiveFileReferences(path, 62)).thenReturn(0);
        FileDeletionTaskProcessor processor =
                processor(tasks, courses, new FileStorageUtil(uploadRoot.toString(), "test"));

        assertTrue(processor.process(7));
        assertTrue(processor.process(8));

        assertFalse(Files.exists(file));
        verify(tasks).deleteTask(7);
        verify(tasks).deleteTask(8);
    }

    @Test
    void physicalDeleteFailureKeepsCourseTaskForRetry() throws Exception {
        String path = "protected/materials/busy.pdf";
        write(path, "%PDF-busy");
        FileStorageUtil storage = spy(new FileStorageUtil(uploadRoot.toString(), "test"));
        doThrow(new FileStorageUtil.StorageException("busy")).when(storage).deleteFile(path);
        FileDeletionTaskMapper tasks = mock(FileDeletionTaskMapper.class);
        CourseDetailMapper courses = mock(CourseDetailMapper.class);
        when(tasks.findByIdForUpdate(3)).thenReturn(task(3, FileDeletionTaskProcessor.COURSE_ORIGINAL, 43, path));
        when(courses.getCourseByIdIncludingDeletedForUpdate(43)).thenReturn(course(43, 0, path));

        assertThrows(FileStorageUtil.StorageException.class, () -> processor(tasks, courses, storage).process(3));
        verify(tasks, never()).deleteTask(3);
    }

    @Test
    void orphanRecoveryNeverDeletesAFileThatBecameActivelyReferenced() throws Exception {
        String path = "images/materials/committed.jpg";
        Path file = write(path, "image");
        FileDeletionTaskMapper tasks = mock(FileDeletionTaskMapper.class);
        CourseDetailMapper courses = mock(CourseDetailMapper.class);
        when(tasks.findByIdForUpdate(4)).thenReturn(task(4, FileDeletionTaskProcessor.COURSE_ORPHAN, 99, path));
        when(tasks.deleteTask(4)).thenReturn(1);
        when(courses.countActiveFileReferences(path, null)).thenReturn(1);

        assertTrue(processor(tasks, courses, new FileStorageUtil(uploadRoot.toString(), "test")).process(4));

        assertTrue(Files.exists(file));
        verify(tasks).deleteTask(4);
    }

    @Test
    void publicImageLedgerDeletionKeepsPhysicalFileUsedByActiveCourse() throws Exception {
        String path = "images/7/99999999-9999-9999-9999-999999999999.jpg";
        Path file = write(path, "shared-image");
        PublicImageQuotaMapper quota = mock(PublicImageQuotaMapper.class);
        CourseDetailMapper courses = mock(CourseDetailMapper.class);
        com.vihu.ganlu.entitys.PublicImageAssetEntity asset =
                new com.vihu.ganlu.entitys.PublicImageAssetEntity();
        asset.setAssetId(77L);
        asset.setRelativePath(path);
        asset.setOwnerUserId(7);
        asset.setFileSize(12L);
        when(quota.findAssetByIdForUpdate(77)).thenReturn(asset);
        when(quota.deleteAsset(77)).thenReturn(1);
        when(quota.releasePermanentQuota(7, 12)).thenReturn(1);
        when(courses.countActiveFileReferences(path, null)).thenReturn(1);
        PublicImageAssetDeletionService service =
                new PublicImageAssetDeletionService(
                        new FileStorageUtil(uploadRoot.toString(), "test"), quota, courses);

        service.deletePhysicalFileThenReleaseQuota(77);

        assertTrue(Files.exists(file));
        verify(quota).deleteAsset(77);
        verify(quota).releasePermanentQuota(7, 12);
    }

    @Test
    void simulatedCommitFailurePreservesStagingAndTriggersPersistentRecovery() throws Exception {
        FileStorageUtil storage = new FileStorageUtil(uploadRoot.toString(), "test");
        MaterialUploadStorageService uploads = mock(MaterialUploadStorageService.class);
        FileDeletionTaskService tasks = mock(FileDeletionTaskService.class);
        AtomicLong taskIds = new AtomicLong(10);
        when(tasks.enqueueCourseOrphanCleanup(any(), anyInt(), anyLong()))
                .thenAnswer(ignored -> taskIds.incrementAndGet());
        Path stagedCover = write("material-staging/7/COVER/cover.jpg", "cover");
        Path stagedMaterial = write("material-staging/7/MATERIAL/file.pdf", "%PDF-file");
        MaterialUploadStorageService.StagedFile cover = staged(7, "COVER", stagedCover, fileInfo("cover.jpg", "jpg", 5));
        MaterialUploadStorageService.StagedFile material = staged(7, "MATERIAL", stagedMaterial, fileInfo("file.pdf", "pdf", 9));
        when(uploads.loadStagedFile(7, "COVER", "11111111-1111-1111-1111-111111111111")).thenReturn(cover);
        when(uploads.loadStagedFile(7, "MATERIAL", "22222222-2222-2222-2222-222222222222")).thenReturn(material);
        CourseDetailMapper mapper = mock(CourseDetailMapper.class);
        when(mapper.insertCourseDetail(any())).thenAnswer(invocation -> {
            ((CourseDetailEntity) invocation.getArgument(0)).setId(51);
            return 1;
        });
        TransactionSynchronizationManager.initSynchronization();

        service(mapper, storage, uploads, tasks).createMaterial(request(), uploader());
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();

        assertTrue(Files.exists(stagedCover));
        assertTrue(Files.exists(stagedMaterial));
        verify(uploads, never()).consumeStagedFile(any());
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(tasks, times(3)).retryNow(anyLong());
        verify(tasks, never()).cancelTask(anyLong());
    }

    private CourseDetailServiceImpl service(CourseDetailMapper mapper, FileStorageUtil storage,
                                            MaterialUploadStorageService uploads, FileDeletionTaskService tasks) {
        return new CourseDetailServiceImpl(mapper, mock(CourseService.class), storage,
                mock(MaterialFileValidator.class), mock(OfficePreviewService.class), uploads, tasks);
    }

    private FileDeletionTaskProcessor processor(FileDeletionTaskMapper tasks, CourseDetailMapper courses,
                                                FileStorageUtil storage) {
        return new FileDeletionTaskProcessor(tasks,
                new PublicImageAssetDeletionService(
                        storage, mock(PublicImageQuotaMapper.class), courses),
                mock(TeamMediaMapper.class), mock(TeamMediaQuotaMapper.class), storage, courses);
    }

    private MaterialUploadStorageService.StagedFile staged(
            int userId, String purpose, Path path, UploadedFileInfo info) throws Exception {
        Constructor<MaterialUploadStorageService.StagedFile> constructor =
                MaterialUploadStorageService.StagedFile.class.getDeclaredConstructor(
                        int.class, String.class, Path.class, Path.class, Path.class, UploadedFileInfo.class);
        constructor.setAccessible(true);
        return constructor.newInstance(userId, purpose, path,
                path.resolveSibling(info.getToken() + ".properties"),
                path.resolveSibling(info.getChecksum() + ".token"), info);
    }

    private UploadedFileInfo fileInfo(String name, String extension, long size) {
        UploadedFileInfo info = new UploadedFileInfo();
        info.setToken(UUID.randomUUID().toString());
        info.setChecksum("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        info.setOriginalName(name);
        info.setExtension(extension);
        info.setMimeType("application/octet-stream");
        info.setSize(size);
        return info;
    }

    private MaterialCreateRequest request() {
        MaterialCreateRequest request = new MaterialCreateRequest();
        request.setTitle("测试课件");
        request.setCourseType(2);
        request.setCustomSubject("测试科目");
        request.setYear(Year.now().getValue());
        request.setCoverToken("11111111-1111-1111-1111-111111111111");
        request.setFileToken("22222222-2222-2222-2222-222222222222");
        return request;
    }

    private UserEntity uploader() {
        UserEntity user = new UserEntity();
        user.setId(7);
        user.setUsername("tester");
        return user;
    }

    private CourseDetailEntity course(int id, int status, String originalPath) {
        CourseDetailEntity course = new CourseDetailEntity();
        course.setId(id);
        course.setStatus(status);
        course.setUploaderUserId(7);
        course.setOriginalFilePath(originalPath);
        course.setThumbnailUrl("images/materials/cover-" + id + ".jpg");
        course.setPreviewFilePath("protected/material-previews/preview-" + id + ".pdf");
        course.setFileSize(9L);
        return course;
    }

    private CourseDetailEntity sharedCourse(int id) {
        CourseDetailEntity course = course(id, 1, "protected/materials/shared.pdf");
        course.setThumbnailUrl("images/materials/shared.jpg");
        course.setPreviewFilePath("protected/material-previews/shared.pdf");
        return course;
    }

    private FileDeletionTaskEntity task(long id, String type, long assetId, String path) {
        FileDeletionTaskEntity task = new FileDeletionTaskEntity();
        task.setId(id);
        task.setAssetType(type);
        task.setAssetId(assetId);
        task.setRelativePath(path);
        task.setOwnerUserId(7);
        task.setFileSize(9L);
        task.setRetryCount(0);
        return task;
    }

    private Path write(String relativePath, String content) throws Exception {
        Path file = uploadRoot.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return file;
    }
}
