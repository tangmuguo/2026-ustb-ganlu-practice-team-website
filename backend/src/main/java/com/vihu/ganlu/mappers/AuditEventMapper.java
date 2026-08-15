package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.AuditEventEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AuditEventMapper {
    int insert(AuditEventEntity event);
    List<AuditEventEntity> findRecent(@Param("offset") int offset, @Param("limit") int limit);
    int countRecent();
    int deleteExpiredUnpreserved();
}
