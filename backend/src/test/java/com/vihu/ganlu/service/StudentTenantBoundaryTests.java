package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.StudentProvisionRequest;
import com.vihu.ganlu.entitys.StudentTeamAssignmentEntity;
import com.vihu.ganlu.entitys.StudentUpdateRequest;
import com.vihu.ganlu.entitys.StudentVerificationRequest;
import com.vihu.ganlu.entitys.TeamEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.mappers.StudentTeamAssignmentMapper;
import com.vihu.ganlu.mappers.TeamMapper;
import com.vihu.ganlu.mappers.UserMapper;
import com.vihu.ganlu.service.impl.PublicImageLifecycleService;
import com.vihu.ganlu.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentTenantBoundaryTests {
    private UserMapper userMapper;
    private TeamMapper teamMapper;
    private StudentTeamAssignmentMapper assignmentMapper;
    private PasswordEncoder passwordEncoder;
    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        teamMapper = mock(TeamMapper.class);
        assignmentMapper = mock(StudentTeamAssignmentMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new UserServiceImpl(userMapper, passwordEncoder, mock(PublicImageLifecycleService.class),
                teamMapper, assignmentMapper);
    }

    @Test
    void teamListUsesOnlyTheTeamResolvedFromTheCurrentAccount() {
        UserEntity teamActor = teamActor(41);
        when(teamMapper.findOwnedTeamByOwnerUserId(41)).thenReturn(team(7, 41));
        UserEntity ownStudent = student(101);
        when(userMapper.findStudentsByActiveTeam(7)).thenReturn(Collections.singletonList(ownStudent));

        assertEquals(Collections.singletonList(101),
                java.util.Arrays.asList(service.findManageableStudents(teamActor).get(0).getId()));

        verify(userMapper).findStudentsByActiveTeam(7);
        verify(userMapper, never()).findUserByLevel(2);
    }

    @Test
    void teamCannotUpdateOrDeleteAStudentOutsideItsActiveAssignment() {
        UserEntity teamActor = teamActor(41);
        when(teamMapper.findOwnedTeamByOwnerUserId(41)).thenReturn(team(7, 41));
        when(userMapper.findStudentByIdForTeamForUpdate(202, 7)).thenReturn(null);

        assertThrows(SecurityException.class, () -> service.updateStudent(202, updateRequest(), teamActor));
        assertThrows(SecurityException.class, () -> service.deleteStudents(Collections.singletonList(202), teamActor));

        verify(userMapper, never()).updateStudentByIdForTeam(any(UserEntity.class), eq(7));
        verify(assignmentMapper, never()).revokeActiveAssignment(any(Integer.class), any(Integer.class), any(Integer.class));
        verify(userMapper, never()).deleteStudentAfterAssignmentRevoked(202);
    }

    @Test
    void teamCannotChooseAnotherTeamWhenProvisioningAStudent() {
        UserEntity teamActor = teamActor(41);
        when(teamMapper.findOwnedTeamByOwnerUserId(41)).thenReturn(team(7, 41));
        when(userMapper.countByUsername("student-200")).thenReturn(0);
        when(userMapper.countByPhone("13800138000")).thenReturn(0);
        when(passwordEncoder.encode("safe-password")).thenReturn("bcrypt-value");
        when(userMapper.addUser(any(UserEntity.class))).thenAnswer(invocation -> {
            ((UserEntity) invocation.getArgument(0)).setId(200);
            return 1;
        });
        when(assignmentMapper.insertActiveAssignment(any(StudentTeamAssignmentEntity.class))).thenReturn(1);

        StudentProvisionRequest request = provisionRequest();
        request.setTeamId(999);
        service.provisionStudent(request, teamActor);

        org.mockito.ArgumentCaptor<StudentTeamAssignmentEntity> assignment =
                org.mockito.ArgumentCaptor.forClass(StudentTeamAssignmentEntity.class);
        verify(assignmentMapper).insertActiveAssignment(assignment.capture());
        assertEquals(7, assignment.getValue().getTeamId());
        assertEquals(41, assignment.getValue().getAssignedByUserId());
        assertEquals(200, assignment.getValue().getStudentUserId());
    }

    @Test
    void administratorMustSupplyAnExistingActiveTeamBeforeAnyStudentWrite() {
        UserEntity administrator = administrator(1);

        assertThrows(IllegalArgumentException.class,
                () -> service.provisionStudent(provisionRequest(), administrator));

        verify(teamMapper, never()).findById(any(Integer.class));
        verify(userMapper, never()).addUser(any(UserEntity.class));
        verify(assignmentMapper, never()).insertActiveAssignment(any(StudentTeamAssignmentEntity.class));
    }

    @Test
    void administratorCannotUseAnUnknownOrArchivedTeam() {
        UserEntity administrator = administrator(1);

        when(teamMapper.findById(404)).thenReturn(null);
        StudentProvisionRequest unknownTeam = provisionRequest();
        unknownTeam.setTeamId(404);
        assertThrows(IllegalArgumentException.class,
                () -> service.provisionStudent(unknownTeam, administrator));

        TeamEntity archived = team(9, 41);
        archived.setStatus(TeamEntity.Status.ARCHIVED);
        when(teamMapper.findById(9)).thenReturn(archived);
        StudentProvisionRequest archivedTeam = provisionRequest();
        archivedTeam.setTeamId(9);
        assertThrows(IllegalArgumentException.class,
                () -> service.provisionStudent(archivedTeam, administrator));

        verify(userMapper, never()).addUser(any(UserEntity.class));
        verify(assignmentMapper, never()).insertActiveAssignment(any(StudentTeamAssignmentEntity.class));
    }

    @Test
    void unverifiedTeamAccountCannotProvisionEvenWhenItOwnsAnActiveTeam() {
        UserEntity teamActor = teamActor(41);
        teamActor.setVerificationStatus("PENDING");
        when(teamMapper.findOwnedTeamByOwnerUserId(41)).thenReturn(team(7, 41));

        assertThrows(SecurityException.class,
                () -> service.provisionStudent(provisionRequest(), teamActor));

        verify(userMapper, never()).addUser(any(UserEntity.class));
        verify(assignmentMapper, never()).insertActiveAssignment(any(StudentTeamAssignmentEntity.class));
    }

    @Test
    void onlyAdministratorCanWriteVerificationOrGuardianConsentEvidence() {
        StudentVerificationRequest request = new StudentVerificationRequest();
        request.setVerificationStatus("VERIFIED");
        request.setVerificationMethod("OFFLINE_SCHOOL_CHECK");
        request.setGuardianConsentStatus("CONSENTED");
        request.setGuardianConsentVersion("2026-08");

        assertThrows(SecurityException.class, () -> service.updateStudentVerification(201, request, teamActor(41)));

        verify(userMapper, never()).updateStudentVerification(any(Integer.class), any(Integer.class), any(), any(), any(), any(), any());
        verify(userMapper, never()).insertConsentRecord(any(Integer.class), any(), any(), any(Boolean.class), any(Integer.class), any());
    }

    private UserEntity teamActor(int id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setLevel(1);
        user.setVerificationStatus("VERIFIED");
        return user;
    }

    private UserEntity administrator(int id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setLevel(0);
        return user;
    }

    private UserEntity student(int id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername("student-" + id);
        user.setLevel(2);
        user.setVerificationStatus("PENDING");
        user.setGuardianConsentStatus("PENDING");
        return user;
    }

    private TeamEntity team(int id, int ownerUserId) {
        TeamEntity team = new TeamEntity();
        team.setId(id);
        team.setOwnerUserId(ownerUserId);
        team.setStatus(TeamEntity.Status.DRAFT);
        return team;
    }

    private StudentUpdateRequest updateRequest() {
        StudentUpdateRequest request = new StudentUpdateRequest();
        request.setUsername("student-202");
        request.setRealname("学生姓名");
        request.setBelongschool("示例小学");
        request.setGrade("三年级");
        return request;
    }

    private StudentProvisionRequest provisionRequest() {
        StudentProvisionRequest request = new StudentProvisionRequest();
        request.setUsername("student-200");
        request.setRealname("学生姓名");
        request.setBelongschool("示例小学");
        request.setGrade("三年级");
        request.setPhone("13800138000");
        request.setPassword("safe-password");
        request.setConfirmPassword("safe-password");
        return request;
    }
}
