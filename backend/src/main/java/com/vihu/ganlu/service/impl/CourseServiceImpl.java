package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.CourseEntity;
import com.vihu.ganlu.mappers.CourseMapper;
import com.vihu.ganlu.service.CourseService;
import com.vihu.ganlu.utils.GeneralCourseSubjectPolicy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class CourseServiceImpl implements CourseService {
    private static final int MAX_ACTIVE_COURSES = 12;

    private final CourseMapper courseMapper;

    public CourseServiceImpl(CourseMapper courseMapper) {
        this.courseMapper = courseMapper;
    }

    @Override
    public List<CourseEntity> getActiveCourses() {
        // 旧科目可继续保留给历史课件关联，但不能重新出现在新增课件的选择项中。
        return courseMapper.getActiveCourses().stream()
                .filter(course -> GeneralCourseSubjectPolicy.isSupported(course.getCourseName()))
                .collect(Collectors.toList());
    }

    @Override
    public List<CourseEntity> getAllCourses() {
        return courseMapper.getAllCourses();
    }

    @Override
    public CourseEntity getCourseById(int id) {
        return courseMapper.getCourseById(id);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public CourseEntity addCourse(String courseName) {
        String normalizedName = normalizeName(courseName);
        if (courseMapper.countByName(normalizedName, null) > 0) {
            throw new IllegalStateException("科目名称已存在");
        }
        if (courseMapper.countActiveCourses() >= MAX_ACTIVE_COURSES) {
            throw new IllegalStateException("启用科目数量不能超过 12 个");
        }
        CourseEntity course = new CourseEntity();
        course.setCourseName(normalizedName);
        course.setStatus(1);
        try {
            courseMapper.insertCourse(course);
        } catch (DuplicateKeyException duplicate) {
            throw new IllegalStateException("科目名称已存在", duplicate);
        }
        return course;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public CourseEntity updateCourse(int id, String courseName, Integer status) {
        CourseEntity existing = courseMapper.getCourseById(id);
        if (existing == null) {
            throw new NoSuchElementException("科目不存在");
        }
        String normalizedName = StringUtils.hasText(courseName)
                ? normalizeName(courseName)
                : existing.getCourseName();
        int normalizedStatus = status == null ? existing.getStatus() : status;
        if (normalizedStatus != 0 && normalizedStatus != 1) {
            throw new IllegalArgumentException("科目状态只能是 0 或 1");
        }
        if (courseMapper.countByName(normalizedName, id) > 0) {
            throw new IllegalStateException("科目名称已存在");
        }
        if (existing.getStatus() == 0 && normalizedStatus == 1
                && courseMapper.countActiveCourses() >= MAX_ACTIVE_COURSES) {
            throw new IllegalStateException("启用科目数量不能超过 12 个");
        }
        existing.setCourseName(normalizedName);
        existing.setStatus(normalizedStatus);
        try {
            courseMapper.updateCourse(existing);
        } catch (DuplicateKeyException duplicate) {
            throw new IllegalStateException("科目名称已存在", duplicate);
        }
        return existing;
    }

    private String normalizeName(String courseName) {
        if (!StringUtils.hasText(courseName)) {
            throw new IllegalArgumentException("科目名称不能为空");
        }
        String normalized = courseName.trim();
        if (normalized.length() < 1 || normalized.length() > 20) {
            throw new IllegalArgumentException("科目名称长度应为 1～20 字");
        }
        return normalized;
    }
}
