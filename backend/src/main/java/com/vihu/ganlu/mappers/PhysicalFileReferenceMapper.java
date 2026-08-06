package com.vihu.ganlu.mappers;

import org.apache.ibatis.annotations.Param;

/** One normalization rule for every business that can keep a physical upload alive. */
public interface PhysicalFileReferenceMapper {
    int countActiveCourseReferences(
            @Param("relativePath") String relativePath,
            @Param("excludeCourseId") Integer excludeCourseId);

    int countPublicBusinessReferences(@Param("relativePath") String relativePath);

    int countPublicAssetReferences(
            @Param("relativePath") String relativePath,
            @Param("excludeAssetId") Long excludeAssetId);
}
