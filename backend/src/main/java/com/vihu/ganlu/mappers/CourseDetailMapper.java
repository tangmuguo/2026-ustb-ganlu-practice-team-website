package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.CourseDetailEntity;
import com.vihu.ganlu.entitys.MaterialSearchQuery;

import java.util.List;

public interface CourseDetailMapper {
    int insertCourseDetail(CourseDetailEntity courseDetail);

    List<CourseDetailEntity> search(MaterialSearchQuery query);

    CourseDetailEntity getCourseById(int id);

    int softDeleteCourseById(int id);
}
