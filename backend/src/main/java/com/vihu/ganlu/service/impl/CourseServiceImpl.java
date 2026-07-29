package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.CourseEntity;
import com.vihu.ganlu.mappers.CourseMapper;
import com.vihu.ganlu.service.CourseService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class CourseServiceImpl implements CourseService {
    @Resource
    CourseMapper courseMapper;

    @Override
    public List<CourseEntity> getAllCourses() {
        return courseMapper.getAllCourses();
    }
}
