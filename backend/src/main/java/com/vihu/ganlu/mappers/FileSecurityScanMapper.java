package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.FileSecurityScanEntity;
import org.apache.ibatis.annotations.Param;

public interface FileSecurityScanMapper {
    int upsert(FileSecurityScanEntity record);

    FileSecurityScanEntity findByPath(@Param("relativePath") String relativePath);

    int updatePath(@Param("fromPath") String fromPath, @Param("toPath") String toPath);
}
