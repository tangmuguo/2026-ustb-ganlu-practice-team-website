package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.privacy.PrivacyRequestCreateRequest;
import com.vihu.ganlu.entitys.privacy.PrivacyRequestEntity;
import com.vihu.ganlu.entitys.privacy.PrivacyRequestResolutionRequest;
import com.vihu.ganlu.mappers.MediaPrivacyConsentMapper;
import com.vihu.ganlu.mappers.PrivacyRequestMapper;
import com.vihu.ganlu.mappers.UserMapper;
import com.vihu.ganlu.service.impl.PrivacyRequestServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class PrivacyRequestServiceTests {
    @Test
    void anonymousCannotCreateOrListOwnTickets() {
        PrivacyRequestMapper mapper = mock(PrivacyRequestMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        PrivacyRequestService service = new PrivacyRequestServiceImpl(mapper, userMapper);

        assertThrows(SecurityException.class, () -> service.create(create("CORRECTION"), null));
        assertThrows(SecurityException.class, () -> service.findMine(1, 20, null));
        verify(mapper, never()).insert(any(PrivacyRequestEntity.class));
    }

    @Test
    void nonAdministratorCannotReadOrProcessAdministratorQueue() {
        PrivacyRequestService service = new PrivacyRequestServiceImpl(
                mock(PrivacyRequestMapper.class), mock(UserMapper.class));
        UserEntity student = user(7, 2);

        assertThrows(SecurityException.class, () -> service.findRecent(null, 1, 20, student));
        assertThrows(SecurityException.class, () -> service.countRecent(null, student));
        assertThrows(SecurityException.class, () -> service.process(1,
                resolution("APPROVED", "MANUAL_REVIEW", "已核验"), student));
    }

    @Test
    void ownLookupAlwaysUsesTokenUserAndDoesNotRevealAnotherUsersTicket() {
        PrivacyRequestMapper mapper = mock(PrivacyRequestMapper.class);
        PrivacyRequestService service = new PrivacyRequestServiceImpl(mapper, mock(UserMapper.class));
        UserEntity student = user(7, 2);

        when(mapper.findByIdForRequester(42L, 7)).thenReturn(null);

        assertNull(service.findMineById(42L, student));
        verify(mapper).findByIdForRequester(42L, 7);
        verify(mapper, never()).findById(42L);
    }

    @Test
    void withdrawalRequiresConsentTypeAndBindsTicketToTokenUser() {
        PrivacyRequestMapper mapper = mock(PrivacyRequestMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        MediaPrivacyConsentMapper mediaMapper = mock(MediaPrivacyConsentMapper.class);
        PrivacyRequestService service = new PrivacyRequestServiceImpl(mapper, userMapper, mediaMapper);
        UserEntity student = user(7, 2);
        PrivacyRequestCreateRequest request = create("WITHDRAW_CONSENT");

        assertThrows(IllegalArgumentException.class, () -> service.create(request, student));
        request.setConsentType("GUARDIAN");
        when(mapper.countOpenWithdrawal(7, "GUARDIAN")).thenReturn(0);
        when(userMapper.withdrawConsentAndInvalidateSession(7, "GUARDIAN")).thenReturn(1);
        when(mapper.insert(any(PrivacyRequestEntity.class))).thenAnswer(invocation -> {
            PrivacyRequestEntity entity = invocation.getArgument(0);
            entity.setId(42L);
            return 1;
        });

        assertEquals(42L, service.create(request, student));
        ArgumentCaptor<PrivacyRequestEntity> captor = ArgumentCaptor.forClass(PrivacyRequestEntity.class);
        verify(mapper).insert(captor.capture());
        assertEquals(7, captor.getValue().getRequesterUserId());
        assertEquals("GUARDIAN", captor.getValue().getConsentType());
        verify(userMapper).withdrawConsentAndInvalidateSession(7, "GUARDIAN");
        verify(mediaMapper).withdrawAllForSubject(7, 7);
    }

    @Test
    void administratorApprovalOnlyRecordsImmediateWithdrawalOutcome() {
        PrivacyRequestMapper mapper = mock(PrivacyRequestMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        MediaPrivacyConsentMapper mediaMapper = mock(MediaPrivacyConsentMapper.class);
        PrivacyRequestServiceImpl service = new PrivacyRequestServiceImpl(mapper, userMapper, mediaMapper);
        PrivacyRequestEntity ticket = new PrivacyRequestEntity();
        ticket.setId(42L);
        ticket.setRequesterUserId(7);
        ticket.setRequestType("WITHDRAW_CONSENT");
        ticket.setConsentType("GUARDIAN");
        ticket.setStatus("OPEN");
        when(mapper.findByIdForUpdate(42L)).thenReturn(ticket);
        when(mapper.updateDecision(eq(42L), eq(1), eq("APPROVED"), eq("WITHDRAWAL_APPROVED"),
                eq("撤回已核验"), eq(null))).thenReturn(1);

        service.process(42L, resolution("APPROVED", "WITHDRAWAL_APPROVED", "撤回已核验"), user(1, 0));

        verify(userMapper, never()).withdrawConsentAndInvalidateSession(anyInt(), anyString());
        verify(mediaMapper, never()).withdrawAllForSubject(anyInt(), anyInt());
        verify(mapper).updateDecision(42L, 1, "APPROVED", "WITHDRAWAL_APPROVED", "撤回已核验", null);
    }

    @Test
    void approvedDeletionOnlyRecordsRetentionDecisionAndNeverDeletesRows() {
        PrivacyRequestMapper mapper = mock(PrivacyRequestMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        PrivacyRequestServiceImpl service = new PrivacyRequestServiceImpl(mapper, userMapper);
        PrivacyRequestEntity ticket = new PrivacyRequestEntity();
        ticket.setId(9L);
        ticket.setRequesterUserId(8);
        ticket.setRequestType("DELETION");
        ticket.setStatus("PROCESSING");
        when(mapper.findByIdForUpdate(9L)).thenReturn(ticket);
        when(mapper.updateDecision(eq(9L), eq(1), eq("APPROVED"), eq("DELETION_REVIEWED"),
                eq("已完成负责人和保全判断"), eq("PRESERVE_UNTIL_REVIEW"))).thenReturn(1);

        service.process(9L, resolution("APPROVED", "DELETION_REVIEWED", "已完成负责人和保全判断"), user(1, 0));

        verify(userMapper, never()).withdrawConsentAndInvalidateSession(anyInt(), anyString());
        verify(mapper).updateDecision(9L, 1, "APPROVED", "DELETION_REVIEWED",
                "已完成负责人和保全判断", "PRESERVE_UNTIL_REVIEW");
    }

    private PrivacyRequestCreateRequest create(String type) {
        PrivacyRequestCreateRequest request = new PrivacyRequestCreateRequest();
        request.setRequestType(type);
        request.setDescription("请处理我的隐私权利申请");
        return request;
    }

    private PrivacyRequestResolutionRequest resolution(String status, String code, String reason) {
        PrivacyRequestResolutionRequest request = new PrivacyRequestResolutionRequest();
        request.setStatus(status);
        request.setDecisionCode(code);
        request.setDecisionReason(reason);
        return request;
    }

    private UserEntity user(int id, int level) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setLevel(level);
        return user;
    }
}
