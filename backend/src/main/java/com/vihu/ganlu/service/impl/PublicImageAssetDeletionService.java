package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.PublicImageAssetEntity;
import com.vihu.ganlu.mappers.PhysicalFileReferenceMapper;
import com.vihu.ganlu.mappers.PublicImageQuotaMapper;
import com.vihu.ganlu.utils.FileStorageUtil;
import com.vihu.ganlu.utils.MaterialPathPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;

@Service
public class PublicImageAssetDeletionService {
    private final FileStorageUtil fileStorageUtil;
    private final PublicImageQuotaMapper quotaMapper;
    private final PhysicalFileReferenceMapper physicalFileReferenceMapper;

    public PublicImageAssetDeletionService(
            FileStorageUtil fileStorageUtil,
            PublicImageQuotaMapper quotaMapper,
            PhysicalFileReferenceMapper physicalFileReferenceMapper) {
        this.fileStorageUtil = fileStorageUtil;
        this.quotaMapper = quotaMapper;
        this.physicalFileReferenceMapper = physicalFileReferenceMapper;
    }

    @Transactional
    public void deletePhysicalFileThenReleaseQuota(long assetId) {
        PublicImageAssetEntity asset = quotaMapper.findAssetByIdForUpdate(assetId);
        if (asset == null) return;
        String relativePath = asset.getRelativePath();
        String normalized = MaterialPathPolicy.normalizeLocalPath(relativePath);
        if (normalized != null
                && physicalFileReferenceMapper.countPublicBusinessReferences(normalized) > 0) {
            return; // Keep the ledger/quota until the final public reference is removed.
        }
        boolean usedByAnotherPublicAsset = normalized != null
                && physicalFileReferenceMapper.countPublicAssetReferences(normalized, assetId) > 0;
        boolean usedByActiveCourse = normalized != null
                && physicalFileReferenceMapper.countActiveCourseReferences(normalized, null) > 0;
        if (!usedByAnotherPublicAsset && !usedByActiveCourse) {
            if (Files.exists(fileStorageUtil.loadFile(relativePath))) {
                fileStorageUtil.deleteFile(relativePath);
            }
            if (Files.exists(fileStorageUtil.loadFile(relativePath))) {
                throw new IllegalStateException("图片物理文件删除失败，配额未释放");
            }
        }
        if (quotaMapper.deleteAsset(asset.getAssetId()) != 1) {
            throw new IllegalStateException("删除公共图片资源账本失败");
        }
        if (quotaMapper.releasePermanentQuota(asset.getOwnerUserId(), asset.getFileSize()) != 1) {
            throw new IllegalStateException("释放公共图片配额失败");
        }
    }
}
