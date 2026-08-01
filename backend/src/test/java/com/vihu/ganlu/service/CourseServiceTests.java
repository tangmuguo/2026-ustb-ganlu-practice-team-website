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
    void categoryWritesUseSerializableTransactions() throws Exception {
        Method add = CourseServiceImpl.class.getMethod("addCourse", String.class);
        Method update = CourseServiceImpl.class.getMethod("updateCourse", int.class, String.class, Integer.class);

        assertEquals(Isolation.SERIALIZABLE, add.getAnnotation(Transactional.class).isolation());
        assertEquals(Isolation.SERIALIZABLE, update.getAnnotation(Transactional.class).isolation());
    }
}
