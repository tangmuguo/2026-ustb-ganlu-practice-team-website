package com.vihu.ganlu.configs;

import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapRunnerTests {

    @Test
    void doesNothingWhenBootstrapVariablesAreEmpty() throws Exception {
        UserService service = mock(UserService.class);

        new AdminBootstrapRunner(service, "", "", "")
                .run(new DefaultApplicationArguments(new String[0]));

        verify(service, never()).findUserByLevel(0);
        verify(service, never()).addUser(any());
    }

    @Test
    void createsFirstAdministratorThroughPasswordHashingService() throws Exception {
        UserService service = mock(UserService.class);
        when(service.findUserByLevel(0)).thenReturn(Collections.emptyList());
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                service, " ganlu-admin ", "safe-password", "13800138000");

        runner.run(new DefaultApplicationArguments(new String[0]));

        org.mockito.ArgumentCaptor<UserEntity> captor = org.mockito.ArgumentCaptor.forClass(UserEntity.class);
        verify(service).addUser(captor.capture());
        assertEquals("ganlu-admin", captor.getValue().getUsername());
        assertEquals("safe-password", captor.getValue().getPassword());
        assertEquals(Integer.valueOf(0), captor.getValue().getLevel());
    }

    @Test
    void neverCreatesSecondAdministrator() throws Exception {
        UserService service = mock(UserService.class);
        when(service.findUserByLevel(0)).thenReturn(Collections.singletonList(new UserEntity()));

        new AdminBootstrapRunner(service, "admin", "safe-password", "")
                .run(new DefaultApplicationArguments(new String[0]));

        verify(service, never()).addUser(any());
    }

    @Test
    void rejectsPartialBootstrapConfiguration() {
        UserService service = mock(UserService.class);
        AdminBootstrapRunner runner = new AdminBootstrapRunner(service, "admin", "", "");

        assertThrows(IllegalStateException.class,
                () -> runner.run(new DefaultApplicationArguments(new String[0])));
    }
}
