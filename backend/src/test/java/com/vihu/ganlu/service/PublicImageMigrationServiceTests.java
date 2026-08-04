package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.PublicImageAssetEntity;
import com.vihu.ganlu.entitys.PublicImageMigrationReport;
import com.vihu.ganlu.entitys.PublicImageReferenceEntity;
import com.vihu.ganlu.mappers.PublicImageMigrationMapper;
import com.vihu.ganlu.mappers.PublicImageQuotaMapper;
import com.vihu.ganlu.service.impl.PublicImageMigrationService;
import com.vihu.ganlu.utils.FileStorageUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PublicImageMigrationServiceTests {
    @TempDir
    Path uploadRoot;

    @Test
    void migrationReadsRealSizesRegistersAllSourcesRepairsZeroAndRebuildsExactQuota() throws Exception {
        String teamPath = "images/11111111-1111-1111-1111-111111111111.jpg";
        String bannerPath = "images/8/22222222-2222-2222-2222-222222222222.png";
        write(teamPath, new byte[]{1, 2, 3});
        write(bannerPath, new byte[]{4, 5, 6, 7});
        PublicImageMigrationMapper references = mock(PublicImageMigrationMapper.class);
        when(references.findBusinessReferences()).thenReturn(Arrays.asList(
                reference("TEAM_IMAGE", 10, teamPath, 7),
                reference("BANNER", 20, bannerPath, null)));
        PublicImageQuotaMapper quota = mock(PublicImageQuotaMapper.class);
        ArrayList<PublicImageAssetEntity> assets = new ArrayList<>();
        PublicImageAssetEntity zeroSized = asset(1L, teamPath, 7, 0L);
        assets.add(zeroSized);
        when(quota.findAllAssetsForUpdate()).thenAnswer(ignored -> new ArrayList<>(assets));
        when(quota.findAllAssets()).thenAnswer(ignored -> new ArrayList<>(assets));
        when(quota.insertAsset(any())).thenAnswer(invocation -> {
            PublicImageAssetEntity inserted = invocation.getArgument(0);
            inserted.setAssetId(2L);
            assets.add(inserted);
            return 1;
        });
        when(quota.updateAssetMetadata(anyLong(), anyInt(), anyLong())).thenAnswer(invocation -> {
            zeroSized.setOwnerUserId(invocation.getArgument(1));
            zeroSized.setFileSize(invocation.getArgument(2));
            return 1;
        });
        FileStorageUtil storage = new FileStorageUtil(uploadRoot.toString(), "test");
        PublicImageMigrationService service = new PublicImageMigrationService(references, quota, storage, true);

        PublicImageMigrationReport before = service.preflight();
        assertTrue(before.isMigrationAllowed(), before.getIssues().toString());
        assertFalse(before.isConsistent());
        assertEquals(1, before.getCandidateCount());
        assertEquals(1, before.getRepairCount());

        PublicImageMigrationReport after = service.migrate();

        assertTrue(after.isConsistent());
        assertEquals(1, after.getMigratedCount());
        assertEquals(1, after.getRepairedCount());
        assertEquals(7L, after.getVerifiedReferenceBytes());
        assertEquals(7L, after.getRegisteredAssetBytes());
        assertEquals(7L, after.getDiskBytes());
        assertEquals(3L, zeroSized.getFileSize());
        verify(quota).deleteAllQuotaRows();
        verify(quota).rebuildQuotaRows();
    }

    @Test
    void sharedPhysicalPathAndMissingFileProduceBlockingListAndNoMutation() throws Exception {
        String shared = "images/7/33333333-3333-3333-3333-333333333333.jpg";
        write(shared, new byte[]{1});
        String missing = "images/7/44444444-4444-4444-4444-444444444444.jpg";
        PublicImageMigrationMapper references = mock(PublicImageMigrationMapper.class);
        when(references.findBusinessReferences()).thenReturn(Arrays.asList(
                reference("BANNER", 1, shared, null),
                reference("NEWS", 2, shared, null),
                reference("USER", 3, missing, 3)));
        PublicImageQuotaMapper quota = mock(PublicImageQuotaMapper.class);
        when(quota.findAllAssets()).thenReturn(new ArrayList<>());
        when(quota.findAllAssetsForUpdate()).thenReturn(new ArrayList<>());
        PublicImageMigrationService service = new PublicImageMigrationService(
                references, quota, new FileStorageUtil(uploadRoot.toString(), "test"), true);

        PublicImageMigrationReport report = service.preflight();

        assertFalse(report.isMigrationAllowed());
        assertTrue(report.getIssues().stream().anyMatch(issue -> "SHARED_PATH".equals(issue.getCode())));
        assertTrue(report.getIssues().stream().anyMatch(issue -> "MISSING_FILE".equals(issue.getCode())));
        assertThrows(PublicImageMigrationService.MigrationBlockedException.class, service::migrate);
        verify(quota, never()).insertAsset(any());
        verify(quota, never()).deleteAllQuotaRows();
    }

    @Test
    void unsupportedLegacyLocalPathIsBlockedInsteadOfSilentlyIgnored() {
        PublicImageMigrationMapper references = mock(PublicImageMigrationMapper.class);
        when(references.findBusinessReferences()).thenReturn(Arrays.asList(
                reference("NEWS", 9, "materials/covers/shared.jpg", null)));
        PublicImageQuotaMapper quota = mock(PublicImageQuotaMapper.class);
        when(quota.findAllAssets()).thenReturn(new ArrayList<>());
        PublicImageMigrationService service = new PublicImageMigrationService(
                references, quota, new FileStorageUtil(uploadRoot.toString(), "test"), false);

        PublicImageMigrationReport report = service.preflight();

        assertFalse(report.isMigrationAllowed());
        assertEquals("UNSUPPORTED_LOCAL_PATH", report.getIssues().get(0).getCode());
        assertThrows(IllegalStateException.class, service::migrate);
    }

    @Test
    void currentMaterialCoverNamespaceIsProtectedButExcludedFromPublicImageLedger() throws Exception {
        String coverPath = "images/materials/55555555-5555-5555-5555-555555555555.jpg";
        write(coverPath, new byte[]{1, 2, 3, 4, 5});
        PublicImageMigrationMapper references = mock(PublicImageMigrationMapper.class);
        when(references.findBusinessReferences()).thenReturn(Collections.emptyList());
        when(references.findCourseCoverReferences()).thenReturn(Collections.singletonList(
                reference("COURSE_COVER", 50, coverPath, 7)));
        PublicImageQuotaMapper quota = mock(PublicImageQuotaMapper.class);
        when(quota.findAllAssets()).thenReturn(Collections.emptyList());
        PublicImageMigrationService service = new PublicImageMigrationService(
                references, quota, new FileStorageUtil(uploadRoot.toString(), "test"), false);

        PublicImageMigrationReport report = service.preflight();

        assertTrue(report.isMigrationAllowed(), report.getIssues().toString());
        assertTrue(report.isConsistent());
        assertEquals(1, report.getCourseCoverReferenceCount());
        assertEquals(1, report.getExcludedCourseCoverFileCount());
        assertEquals(5L, report.getExcludedCourseCoverBytes());
        assertEquals(0, report.getManagedReferenceCount());
        assertEquals(0, report.getRegisteredAssetCount());
        assertEquals(0, report.getDiskFileCount());
        assertEquals(0L, report.getDiskBytes());
        assertEquals(0, report.getCandidateCount());
    }

    @Test
    void historicalMaterialCoverInImagesRootIsProtectedByExactDatabaseReference() throws Exception {
        String coverPath = "images/66666666-6666-6666-6666-666666666666.png";
        write(coverPath, new byte[]{1, 2, 3});
        PublicImageMigrationMapper references = mock(PublicImageMigrationMapper.class);
        when(references.findBusinessReferences()).thenReturn(Collections.emptyList());
        when(references.findCourseCoverReferences()).thenReturn(Collections.singletonList(
                reference("COURSE_COVER", 60, coverPath, null)));
        PublicImageQuotaMapper quota = mock(PublicImageQuotaMapper.class);
        when(quota.findAllAssets()).thenReturn(Collections.emptyList());
        PublicImageMigrationService service = new PublicImageMigrationService(
                references, quota, new FileStorageUtil(uploadRoot.toString(), "test"), false);

        PublicImageMigrationReport report = service.preflight();

        assertTrue(report.isMigrationAllowed(), report.getIssues().toString());
        assertTrue(report.isConsistent());
        assertEquals(1, report.getCourseCoverReferenceCount());
        assertEquals(1, report.getExcludedCourseCoverFileCount());
        assertEquals(0, report.getManagedReferenceCount());
        assertEquals(0, report.getRegisteredAssetCount());
        assertEquals(0, report.getDiskFileCount());
        assertEquals(0L, report.getDiskBytes());
        assertEquals(0, report.getCandidateCount());
    }

    @Test
    void materialCoverCannotSharePhysicalFileWithManagedPublicImage() throws Exception {
        String sharedPath = "images/77777777-7777-7777-7777-777777777777.jpg";
        write(sharedPath, new byte[]{1});
        PublicImageMigrationMapper references = mock(PublicImageMigrationMapper.class);
        when(references.findBusinessReferences()).thenReturn(Collections.singletonList(
                reference("TEAM_IMAGE", 70, sharedPath, 7)));
        when(references.findCourseCoverReferences()).thenReturn(Collections.singletonList(
                reference("COURSE_COVER", 71, sharedPath, 7)));
        PublicImageQuotaMapper quota = mock(PublicImageQuotaMapper.class);
        when(quota.findAllAssets()).thenReturn(Collections.emptyList());
        PublicImageMigrationService service = new PublicImageMigrationService(
                references, quota, new FileStorageUtil(uploadRoot.toString(), "test"), false);

        PublicImageMigrationReport report = service.preflight();

        assertFalse(report.isMigrationAllowed());
        assertTrue(report.getIssues().stream()
                .anyMatch(issue -> "CROSS_DOMAIN_SHARED_PATH".equals(issue.getCode())));
        assertEquals(0, report.getExcludedCourseCoverFileCount());
        assertEquals(1, report.getDiskFileCount());
    }

    private void write(String relativePath, byte[] content) throws Exception {
        Path file = uploadRoot.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }

    private PublicImageReferenceEntity reference(
            String type, int id, String path, Integer ownerHint) {
        PublicImageReferenceEntity reference = new PublicImageReferenceEntity();
        reference.setSourceType(type);
        reference.setSourceId(id);
        reference.setRelativePath(path);
        reference.setOwnerHint(ownerHint);
        return reference;
    }

    private PublicImageAssetEntity asset(long id, String path, int owner, long size) {
        PublicImageAssetEntity asset = new PublicImageAssetEntity();
        asset.setAssetId(id);
        asset.setRelativePath(path);
        asset.setOwnerUserId(owner);
        asset.setFileSize(size);
        return asset;
    }
}
