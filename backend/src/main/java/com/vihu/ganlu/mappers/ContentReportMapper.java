package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.ContentReportEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ContentReportMapper {
    int insert(ContentReportEntity report);
    List<ContentReportEntity> findRecent(@Param("status") String status,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);
    int countRecent(@Param("status") String status);
    int updateResolution(@Param("id") long id,
                         @Param("actorUserId") int actorUserId,
                         @Param("status") String status,
                         @Param("resolutionCode") String resolutionCode,
                         @Param("resolutionNote") String resolutionNote);
}
