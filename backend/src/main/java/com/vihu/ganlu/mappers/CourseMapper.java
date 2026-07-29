package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.CourseEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;


public interface CourseMapper {
    List<CourseEntity> getAllCourses();
}
