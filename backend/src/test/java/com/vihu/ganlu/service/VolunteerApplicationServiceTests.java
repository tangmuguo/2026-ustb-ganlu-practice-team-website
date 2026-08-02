package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.VolunteerApplicationEntity;
import com.vihu.ganlu.entitys.VolunteerApplicationRequest;
import com.vihu.ganlu.mappers.VolunteerApplicationMapper;
import com.vihu.ganlu.service.impl.VolunteerApplicationServiceImpl;
import com.vihu.ganlu.service.impl.VolunteerApplicationServiceImpl.DuplicateApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VolunteerApplicationServiceTests {
    private VolunteerApplicationMapper mapper;
    private VolunteerApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(VolunteerApplicationMapper.class);
        service = new VolunteerApplicationServiceImpl(mapper);
    }

    @Test
    void createsPendingApplicationWithoutExposingExtraState() {
        VolunteerApplicationRequest request = validRequest();
        when(mapper.countActiveByPhone("13800000000")).thenReturn(0);
        when(mapper.insert(any())).thenAnswer(invocation -> {
            VolunteerApplicationEntity entity = invocation.getArgument(0);
            entity.setId(10L);
            return 1;
        });

        VolunteerApplicationEntity result = service.submit(request);

        assertEquals(10L, result.getId());
        assertEquals("PENDING", result.getStatus());
        assertTrue(result.getPrivacyAgreed());
    }

    @Test
    void blocksDuplicateActivePhone() {
        when(mapper.countActiveByPhone("13800000000")).thenReturn(1);
        assertThrows(DuplicateApplicationException.class, () -> service.submit(validRequest()));
        verify(mapper, never()).insert(any());
    }

    @Test
    void validatesAdminStatusUpdate() {
        assertThrows(IllegalArgumentException.class, () -> service.updateStatus(1L, "UNKNOWN"));
        when(mapper.updateStatus(1L, "CONTACTED")).thenReturn(1);
        assertTrue(service.updateStatus(1L, "contacted"));
    }

    private VolunteerApplicationRequest validRequest() {
        VolunteerApplicationRequest request = new VolunteerApplicationRequest();
        request.setName("赵同学");
        request.setPhone("13800000000");
        request.setOrganization("北京科技大学");
        request.setIntroduction("我愿意参与支教并认真准备课程内容。");
        request.setPrivacyAgreed(true);
        return request;
    }
}
