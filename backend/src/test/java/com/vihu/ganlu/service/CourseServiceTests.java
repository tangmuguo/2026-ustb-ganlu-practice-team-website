package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.CourseEntity;
import com.vihu.ganlu.mappers.CourseMapper;
import com.vihu.ganlu.service.impl.CourseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseServiceTests {
    private CourseMapper mapper;
    private CourseServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(CourseMapper.class);
        service = new CourseServiceImpl(mapper);
    }

    @Test
    void rejectsThirteenthActiveCourse() {
        when(mapper.countByName("地理", null)).thenReturn(0);
        when(mapper.countActiveCourses()).thenReturn(12);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.addCourse("地理"));

        assertEquals("启用科目数量不能超过 12 个", error.getMessage());
    }

    @Test
    void addsNormalizedCourseName() {
        when(mapper.countByName("地理", null)).thenReturn(0);
        when(mapper.countActiveCourses()).thenReturn(5);

        CourseEntity added = service.addCourse("  地理  ");

        assertEquals("地理", added.getCourseName());
        assertEquals(1, added.getStatus());
        verify(mapper).insertCourse(added);
    }

    @Test
    void convertsDatabaseUniqueViolationToStableBusinessMessage() {
        when(mapper.countByName("地理", null)).thenReturn(0);
        when(mapper.countActiveCourses()).thenReturn(5);
        doThrow(new DuplicateKeyException("uk_course_name")).when(mapper).insertCourse(org.mockito.ArgumentMatchers.any());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.addCourse("地理"));

        assertEquals("科目名称已存在", error.getMessage());
    }

    @Test
    void exposesOnlyTheThreeSupportedGeneralSubjects() {
        when(mapper.getActiveCourses()).thenReturn(Arrays.asList(
                course("语文"), course("数学"), course("历史"), course("生物"), course("英语")));

        List<String> courseNames = service.getActiveCourses().stream()
                .map(CourseEntity::getCourseName)
                .collect(Collectors.toList());

        assertEquals(Arrays.asList("语文", "数学", "英语"), courseNames);
    }

    @Test
    void categoryWritesUseSerializableTransactions() throws Exception {
        Method add = CourseServiceImpl.class.getMethod("addCourse", String.class);
        Method update = CourseServiceImpl.class.getMethod("updateCourse", int.class, String.class, Integer.class);

        assertEquals(Isolation.SERIALIZABLE, add.getAnnotation(Transactional.class).isolation());
        assertEquals(Isolation.SERIALIZABLE, update.getAnnotation(Transactional.class).isolation());
    }

    private CourseEntity course(String courseName) {
        CourseEntity course = new CourseEntity();
        course.setCourseName(courseName);
        course.setStatus(1);
        return course;
    }
}
