package com.vihu.ganlu.mappers;

import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;

public interface TeamMediaQuotaMapper {
    int ensureGlobalQuotaRow();

    Integer lockGlobalQuotaRow();

    int cleanupUploadReservations(@Param("releasedBefore") Timestamp releasedBefore);

    Integer countActiveUploadReservations();

    Long sumActiveUploadReservationBytes();

    Integer countRecentUploadAttempts(
            @Param("ownerUserId") int ownerUserId,
            @Param("windowStart") Timestamp windowStart);

    int insertUploadReservation(
            @Param("reservationId") String reservationId,
            @Param("ownerUserId") int ownerUserId,
            @Param("reservedBytes") long reservedBytes,
            @Param("expiresAt") Timestamp expiresAt);

    int releaseUploadReservation(@Param("reservationId") String reservationId);

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
