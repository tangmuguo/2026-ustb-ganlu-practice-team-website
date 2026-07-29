package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.CourseDetailEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;


public interface CourseDetailMapper {
    int insertCourseDetail(CourseDetailEntity courseDetail);
    List<CourseDetailEntity> findAllCourse();
    List<CourseDetailEntity> findCourseList();
    CourseDetailEntity getCourseById(int id);
    int deleteCourseById(int id);
}
