package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.PublicImageAssetEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PublicImageQuotaMapper {
    int ensureQuotaRow(@Param("ownerUserId") int ownerUserId);

    int reservePermanentQuota(
            @Param("ownerUserId") int ownerUserId,
            @Param("fileSize") long fileSize,
            @Param("maxFiles") int maxFiles,
            @Param("maxBytes") long maxBytes);

    int insertAsset(PublicImageAssetEntity asset);

    PublicImageAssetEntity findAsset(@Param("relativePath") String relativePath);

    PublicImageAssetEntity findAssetForUpdate(@Param("relativePath") String relativePath);

    PublicImageAssetEntity findAssetByIdForUpdate(@Param("assetId") long assetId);

    List<PublicImageAssetEntity> findAllAssets();

    List<PublicImageAssetEntity> findAllAssetsForUpdate();

    int updateAssetPath(
            @Param("assetId") long assetId,
            @Param("relativePath") String relativePath);

    int deleteAsset(@Param("assetId") long assetId);

    int updateAssetMetadata(
            @Param("assetId") long assetId,
            @Param("ownerUserId") int ownerUserId,
            @Param("fileSize") long fileSize);

    int deleteAllQuotaRows();

    int rebuildQuotaRows();

    int releasePermanentQuota(
            @Param("ownerUserId") int ownerUserId,
            @Param("fileSize") long fileSize);
}
