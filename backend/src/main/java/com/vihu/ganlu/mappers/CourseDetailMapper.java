package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.CourseDetailEntity;
import com.vihu.ganlu.entitys.MaterialSearchQuery;

import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface CourseDetailMapper {
    int insertCourseDetail(CourseDetailEntity courseDetail);

    List<CourseDetailEntity> search(MaterialSearchQuery query);

    CourseDetailEntity getCourseById(int id);

    CourseDetailEntity getCourseByIdForUpdate(int id);

    CourseDetailEntity getCourseByIdIncludingDeletedForUpdate(int id);

    int softDeleteCourseByIdForOwner(@Param("id") int id, @Param("actorUserId") int actorUserId);
    int softDeleteCourseByIdForAdmin(@Param("id") int id, @Param("actorUserId") int actorUserId);
}
