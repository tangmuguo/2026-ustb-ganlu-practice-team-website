package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.VolunteerApplicationEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface VolunteerApplicationMapper {
    int insert(VolunteerApplicationEntity entity);
    int count(@Param("status") String status);
    List<VolunteerApplicationEntity> findPage(
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize);
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
