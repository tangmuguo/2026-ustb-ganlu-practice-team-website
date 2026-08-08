package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.CourseEntity;

import java.util.List;

public interface CourseService {
    List<CourseEntity> getActiveCourses();

    List<CourseEntity> getAllCourses();

    CourseEntity getCourseById(int id);

    CourseEntity addCourse(String courseName);

    CourseEntity updateCourse(int id, String courseName, Integer status);
}
