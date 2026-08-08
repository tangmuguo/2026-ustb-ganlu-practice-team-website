package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.FileDeletionTaskEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FileDeletionTaskMapper {
    int insertTask(FileDeletionTaskEntity task);

    FileDeletionTaskEntity findByAsset(
            @Param("assetType") String assetType,
            @Param("assetId") long assetId);

    FileDeletionTaskEntity findById(@Param("id") long id);

    FileDeletionTaskEntity findByIdForUpdate(@Param("id") long id);

    List<FileDeletionTaskEntity> findRetryable(@Param("limit") int limit);

    List<FileDeletionTaskEntity> findAll(@Param("limit") int limit);

    int markFailure(
            @Param("id") long id,
            @Param("lastError") String lastError,
            @Param("delaySeconds") long delaySeconds);

    int deleteTask(@Param("id") long id);
}
