package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.PublicImageAssetEntity;
import org.apache.ibatis.annotations.Param;

public interface PublicImageQuotaMapper {
    int ensureQuotaRow(@Param("ownerUserId") int ownerUserId);

    int reservePermanentQuota(
            @Param("ownerUserId") int ownerUserId,
            @Param("fileSize") long fileSize,
            @Param("maxFiles") int maxFiles,
            @Param("maxBytes") long maxBytes);

    int insertAsset(
            @Param("relativePath") String relativePath,
            @Param("ownerUserId") int ownerUserId,
            @Param("fileSize") long fileSize);

    PublicImageAssetEntity findAsset(@Param("relativePath") String relativePath);

    int deleteAsset(@Param("relativePath") String relativePath);

    int releasePermanentQuota(
            @Param("ownerUserId") int ownerUserId,
            @Param("fileSize") long fileSize);
}
