package com.vihu.ganlu.mappers;

import org.apache.ibatis.annotations.Param;

public interface TeamMediaQuotaMapper {
    int ensureGlobalQuotaRow();

    int reserveGlobalQuota(
            @Param("fileSize") long fileSize,
            @Param("maxFiles") int maxFiles,
            @Param("maxBytes") long maxBytes);

    int ensureOwnerQuotaRow(@Param("ownerUserId") int ownerUserId);

    int reserveOwnerQuota(
            @Param("ownerUserId") int ownerUserId,
            @Param("fileSize") long fileSize,
            @Param("maxFiles") int maxFiles,
            @Param("maxBytes") long maxBytes);

    int releaseGlobalQuota(@Param("fileSize") long fileSize);

    int releaseOwnerQuota(
            @Param("ownerUserId") int ownerUserId,
            @Param("fileSize") long fileSize);
}
