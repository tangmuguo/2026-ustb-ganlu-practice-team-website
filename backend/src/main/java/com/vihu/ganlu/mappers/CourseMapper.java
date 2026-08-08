package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.CourseEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CourseMapper {
    List<CourseEntity> getActiveCourses();

    List<CourseEntity> getAllCourses();

    CourseEntity getCourseById(int id);

    int countActiveCourses();

    int countByName(@Param("courseName") String courseName, @Param("excludeId") Integer excludeId);

    int insertCourse(CourseEntity course);

    int updateCourse(CourseEntity course);
}
