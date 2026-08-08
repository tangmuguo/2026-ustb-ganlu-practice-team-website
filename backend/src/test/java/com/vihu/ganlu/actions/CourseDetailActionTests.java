package com.vihu.ganlu.actions;

import com.github.pagehelper.PageInfo;
import com.vihu.ganlu.entitys.CourseDetailEntity;
import com.vihu.ganlu.entitys.MaterialCreateRequest;
import com.vihu.ganlu.entitys.MaterialSearchQuery;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.service.CourseDetailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseDetailActionTests {
    private CourseDetailService service;
    private CourseDetailAction action;

    @BeforeEach
    void setUp() {
        service = mock(CourseDetailService.class);
        action = new CourseDetailAction(service);
    }

    @Test
    void publicSearchUsesStandardEnvelope() {
        PageInfo<CourseDetailEntity> page = new PageInfo<>(Collections.emptyList());
        when(service.search(any(MaterialSearchQuery.class))).thenReturn(page);

        ResponseEntity<?> response = action.search(new MaterialSearchQuery());

        assertEquals(200, response.getStatusCodeValue());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(200, body.get("code"));
        assertSame(page, body.get("content"));
    }

    @Test
    void createUsesAuthenticatedUserFromRequestContext() throws Exception {
        UserEntity uploader = new UserEntity();
        uploader.setId(7);
        uploader.setLevel(1);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE, uploader);
        MaterialCreateRequest request = new MaterialCreateRequest();
        CourseDetailEntity created = new CourseDetailEntity();
        created.setId(21);
        created.setPreviewStatus("READY");
        when(service.createMaterial(request, uploader)).thenReturn(created);

        ResponseEntity<?> response = action.create(request, servletRequest);

        assertEquals(201, response.getStatusCodeValue());
        verify(service).createMaterial(request, uploader);
    }

    @Test
    void missingDetailIsReportedAsNotFound() {
        when(service.getCourseById(404)).thenReturn(null);

        NoSuchElementException error = assertThrows(NoSuchElementException.class, () -> action.detail(404));
        ResponseEntity<?> response = action.handleNotFound(error);

        assertEquals(404, response.getStatusCodeValue());
        assertEquals(404, ((Map<?, ?>) response.getBody()).get("code"));
    }

    @Test
    void deleteDelegatesToSoftDeleteService() {
        when(service.deleteCourseById(9)).thenReturn(true);

        ResponseEntity<?> response = action.delete(9);

        assertEquals(200, response.getStatusCodeValue());
        verify(service).deleteCourseById(9);
    }
}
