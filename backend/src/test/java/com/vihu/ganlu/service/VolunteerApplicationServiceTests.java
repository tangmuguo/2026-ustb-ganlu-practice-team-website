package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.VolunteerApplicationEntity;
import com.vihu.ganlu.entitys.VolunteerApplicationRequest;
import com.vihu.ganlu.mappers.VolunteerApplicationMapper;
import com.vihu.ganlu.service.impl.VolunteerApplicationServiceImpl;
import com.vihu.ganlu.service.impl.VolunteerApplicationServiceImpl.DuplicateApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
        when(mapper.insert(any())).thenThrow(new DuplicateKeyException("uk_volunteer_active_phone"));

        assertThrows(DuplicateApplicationException.class, () -> service.submit(validRequest()));
    }

    @Test
    void concurrentSubmissionsStoreOnlyOneActiveApplication() throws Exception {
        AtomicBoolean activePhoneClaimed = new AtomicBoolean(false);
        AtomicInteger storedRows = new AtomicInteger(0);
        CountDownLatch bothAtInsert = new CountDownLatch(2);
        CountDownLatch startInsert = new CountDownLatch(1);
        when(mapper.insert(any())).thenAnswer(invocation -> {
            bothAtInsert.countDown();
            if (!startInsert.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent test did not start");
            }
            if (!activePhoneClaimed.compareAndSet(false, true)) {
                throw new DuplicateKeyException("uk_volunteer_active_phone");
            }
            VolunteerApplicationEntity entity = invocation.getArgument(0);
            entity.setId(20L);
            storedRows.incrementAndGet();
            return 1;
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> submitAndReportSuccess());
            Future<Boolean> second = executor.submit(() -> submitAndReportSuccess());
            assertTrue(bothAtInsert.await(2, TimeUnit.SECONDS));
            startInsert.countDown();

            int successes = (first.get(2, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(2, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
            assertEquals(1, storedRows.get());
        } finally {
            startInsert.countDown();
            executor.shutdownNow();
        }
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

    private boolean submitAndReportSuccess() {
        try {
            service.submit(validRequest());
            return true;
        } catch (DuplicateApplicationException ex) {
            return false;
        }
    }
}
