package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.CourseEntity;
import com.vihu.ganlu.entitys.MaterialCreateRequest;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.mappers.CourseDetailMapper;
import com.vihu.ganlu.service.impl.CourseDetailServiceImpl;
import com.vihu.ganlu.service.impl.FileDeletionTaskService;
import com.vihu.ganlu.service.impl.MaterialUploadStorageService;
import com.vihu.ganlu.utils.FileStorageUtil;
import com.vihu.ganlu.utils.MaterialFileValidator;
import org.junit.jupiter.api.Test;

import java.time.Year;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourseDetailSubjectPolicyTests {

    @Test
    void rejectsLegacySubjectEvenWhenItIsStillActive() throws Exception {
        CourseService courseService = mock(CourseService.class);
        CourseEntity history = new CourseEntity();
        history.setId(4);
        history.setCourseName("历史");
        history.setStatus(1);
        when(courseService.getCourseById(4)).thenReturn(history);

        CourseDetailServiceImpl service = new CourseDetailServiceImpl(
                mock(CourseDetailMapper.class), courseService, mock(FileStorageUtil.class),
                mock(MaterialFileValidator.class), mock(OfficePreviewService.class),
                mock(MaterialUploadStorageService.class), mock(FileDeletionTaskService.class));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.createMaterial(request(), uploader()));

        assertEquals("通识课程仅支持语文、数学、英语", error.getMessage());
    }

    private MaterialCreateRequest request() {
        MaterialCreateRequest request = new MaterialCreateRequest();
        request.setTitle("测试课件");
        request.setCourseType(1);
        request.setCourseId(4);
        request.setYear(Year.now().getValue());
        request.setCoverToken("11111111-1111-1111-1111-111111111111");
        request.setFileToken("22222222-2222-2222-2222-222222222222");
        return request;
    }

    private UserEntity uploader() {
        UserEntity uploader = new UserEntity();
        uploader.setId(7);
        return uploader;
    }
}
