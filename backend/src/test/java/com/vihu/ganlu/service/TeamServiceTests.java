package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.TeamDetailDto;
import com.vihu.ganlu.entitys.TeamEntity;
import com.vihu.ganlu.entitys.TeamPageEntity;
import com.vihu.ganlu.entitys.TeamSaveRequest;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.mappers.TeamMapper;
import com.vihu.ganlu.mappers.TeamPageMapper;
import com.vihu.ganlu.service.impl.TeamPageServiceImpl;
import com.vihu.ganlu.service.impl.TeamServieImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamServiceTests {
    private TeamMapper teamMapper;
    private TeamPageService teamPageService;
    private UserService userService;
    private TeamServieImpl teamService;

    @BeforeEach
    void setUp() {
        teamMapper = mock(TeamMapper.class);
        teamPageService = mock(TeamPageService.class);
        userService = mock(UserService.class);
        teamService = new TeamServieImpl(teamMapper, teamPageService, userService);
    }

    @Test
    void createsTeamAndBindsExactlyOneDetailPage() {
        when(userService.findUserById(7)).thenReturn(user(7, 1));
        when(teamMapper.countByYearAndNameExcludingId("2025", "星火小队", null)).thenReturn(0);
        when(teamMapper.countByOwnerUserIdExcludingId(7, null)).thenReturn(0);
        when(teamMapper.insertTeam(any(TeamEntity.class))).thenAnswer(invocation -> {
            TeamEntity team = invocation.getArgument(0);
            team.setId(10);
            return 1;
        });
        TeamPageEntity page = new TeamPageEntity();
        page.setId(20);
        when(teamPageService.ensureTeamPage(any(TeamEntity.class))).thenReturn(page);

        TeamDetailDto detail = teamService.createTeam(validRequest());

        assertEquals(10, detail.getId());
        assertEquals(20, detail.getPageId());
        ArgumentCaptor<TeamEntity> teamCaptor = ArgumentCaptor.forClass(TeamEntity.class);
        verify(teamPageService).ensureTeamPage(teamCaptor.capture());
        assertEquals(7, teamCaptor.getValue().getOwnerUserId());
        assertEquals(TeamEntity.Status.DRAFT, teamCaptor.getValue().getStatus());
    }

    @Test
    void rejectsDuplicateNameWithinSameYear() {
        when(userService.findUserById(7)).thenReturn(user(7, 1));
        when(teamMapper.countByYearAndNameExcludingId("2025", "星火小队", null)).thenReturn(1);

        assertThrows(DuplicateKeyException.class, () -> teamService.createTeam(validRequest()));
        verify(teamMapper, never()).insertTeam(any(TeamEntity.class));
    }

    @Test
    void rejectsOwnerAlreadyBoundToAnotherTeam() {
        // Item 5: 同一负责人账号已绑别的小队时，create 应拒绝
        when(userService.findUserById(7)).thenReturn(user(7, 1));
        when(teamMapper.countByYearAndNameExcludingId("2025", "星火小队", null)).thenReturn(0);
        when(teamMapper.countByOwnerUserIdExcludingId(7, null)).thenReturn(1); // owner 已被占用

        assertThrows(DuplicateKeyException.class, () -> teamService.createTeam(validRequest()));
        verify(teamMapper, never()).insertTeam(any(TeamEntity.class));
    }

    @Test
    void updateKeepsSameOwnerWithoutConflict() {
        // Item 5: 更新自身时传入 excludeId=当前 teamId，同一 owner 不算冲突
        TeamEntity existing = new TeamEntity();
        existing.setId(10);
        existing.setOwnerUserId(7);
        existing.setStatus(TeamEntity.Status.DRAFT);
        when(teamMapper.findById(10)).thenReturn(existing);
        when(userService.findUserById(7)).thenReturn(user(7, 1));
        when(teamMapper.countByYearAndNameExcludingId("2025", "星火小队", 10)).thenReturn(0);
        when(teamMapper.countByOwnerUserIdExcludingId(7, 10)).thenReturn(0); // 同 owner 改自己
        TeamPageEntity page = new TeamPageEntity();
        page.setId(20);
        when(teamPageService.ensureTeamPage(any(TeamEntity.class))).thenReturn(page);

        TeamDetailDto detail = teamService.updateTeam(10, validRequest());

        assertEquals(20, detail.getPageId());
        verify(teamMapper).updateTeam(any(TeamEntity.class));
    }

    @Test
    void updateRejectsOwnerAlreadyBoundToAnotherTeam() {
        // Item 5: 更新时把 owner 改成已被别的小队占用的账号 → 拒绝
        TeamEntity existing = new TeamEntity();
        existing.setId(10);
        existing.setOwnerUserId(8);
        existing.setStatus(TeamEntity.Status.DRAFT);
        when(teamMapper.findById(10)).thenReturn(existing);
        when(userService.findUserById(7)).thenReturn(user(7, 1));
        when(teamMapper.countByYearAndNameExcludingId("2025", "星火小队", 10)).thenReturn(0);
        when(teamMapper.countByOwnerUserIdExcludingId(7, 10)).thenReturn(1); // owner 7 已绑别的小队

        assertThrows(DuplicateKeyException.class, () -> teamService.updateTeam(10, validRequest()));
        verify(teamMapper, never()).updateTeam(any(TeamEntity.class));
    }

    @Test
    void rejectsOwnerThatIsNotTeamAccount() {
        when(userService.findUserById(7)).thenReturn(user(7, 2));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> teamService.createTeam(validRequest()));

        assertTrue(exception.getMessage().contains("甘露团队账号"));
        verify(teamMapper, never()).insertTeam(any(TeamEntity.class));
    }

    @Test
    void publishedListUsesRequestedYearAndOffset() {
        when(teamMapper.findPublishedByYear("2025", 12L, 12)).thenReturn(Collections.emptyList());
        when(teamMapper.countPublishedByYear("2025")).thenReturn(13L);

        Map<String, Object> result = teamService.getPublishedTeams("2025", 2, 12);

        assertEquals(2, result.get("page"));
        assertEquals(13L, result.get("total"));
        assertEquals(2L, result.get("totalPages"));
        verify(teamMapper).findPublishedByYear("2025", 12L, 12);
    }

    @Test
    void updateReusesTheTeamPageBinding() {
        TeamEntity existing = new TeamEntity();
        existing.setId(10);
        existing.setStatus(TeamEntity.Status.DRAFT);
        when(teamMapper.findById(10)).thenReturn(existing);
        when(userService.findUserById(7)).thenReturn(user(7, 1));
        when(teamMapper.countByYearAndNameExcludingId("2025", "星火小队", 10)).thenReturn(0);
        when(teamMapper.countByOwnerUserIdExcludingId(7, 10)).thenReturn(0);
        TeamPageEntity page = new TeamPageEntity();
        page.setId(20);
        when(teamPageService.ensureTeamPage(any(TeamEntity.class))).thenReturn(page);

        TeamDetailDto detail = teamService.updateTeam(10, validRequest());

        assertEquals(20, detail.getPageId());
        verify(teamMapper).updateTeam(any(TeamEntity.class));
        verify(teamPageService).ensureTeamPage(any(TeamEntity.class));
    }

    @Test
    void archiveKeepsTeamAndSynchronizesPageStatus() {
        TeamEntity existing = new TeamEntity();
        existing.setId(10);
        existing.setName("星火小队");
        existing.setStatus(TeamEntity.Status.PUBLISHED);
        when(teamMapper.findById(10)).thenReturn(existing);
        when(teamPageService.findByTeamId(10)).thenReturn(new TeamPageEntity());

        teamService.archiveTeam(10);

        verify(teamMapper).archiveTeam(10);
        ArgumentCaptor<TeamEntity> teamCaptor = ArgumentCaptor.forClass(TeamEntity.class);
        verify(teamPageService).ensureTeamPage(teamCaptor.capture());
        assertEquals(TeamEntity.Status.ARCHIVED, teamCaptor.getValue().getStatus());
    }

    @Test
    void archiveDoesNotCreateMissingTeamPage() {
        TeamEntity existing = new TeamEntity();
        existing.setId(10);
        existing.setStatus(TeamEntity.Status.PUBLISHED);
        when(teamMapper.findById(10)).thenReturn(existing);
        when(teamPageService.findByTeamId(10)).thenReturn(null);

        teamService.archiveTeam(10);

        verify(teamMapper).archiveTeam(10);
        verify(teamPageService, never()).ensureTeamPage(any(TeamEntity.class));
    }

    @Test
    void archiveIsIdempotentForAlreadyArchivedTeam() {
        TeamEntity existing = new TeamEntity();
        existing.setId(10);
        existing.setStatus(TeamEntity.Status.ARCHIVED);
        when(teamMapper.findById(10)).thenReturn(existing);

        teamService.archiveTeam(10);

        verify(teamMapper, never()).archiveTeam(10);
        verify(teamPageService, never()).findByTeamId(10);
        verify(teamPageService, never()).ensureTeamPage(any(TeamEntity.class));
    }

    @Test
    void updateDoesNotInsertASecondTeamPage() {
        TeamPageMapper pageMapper = mock(TeamPageMapper.class);
        TeamPageServiceImpl pageService = new TeamPageServiceImpl(pageMapper);
        TeamPageEntity existingPage = new TeamPageEntity();
        existingPage.setId(20);
        existingPage.setTeamId(10);
        when(pageMapper.findByTeamId(10)).thenReturn(existingPage);
        TeamEntity team = new TeamEntity();
        team.setId(10);
        team.setName("星火小队");
        team.setStatus(TeamEntity.Status.PUBLISHED);

        TeamPageEntity page = pageService.ensureTeamPage(team);

        assertEquals(20, page.getId());
        assertEquals(TeamPageEntity.Status.PUBLISHED, page.getStatus());
        verify(pageMapper).updateMetadataByTeamId(existingPage);
        verify(pageMapper, never()).insertTeamPage(any(TeamPageEntity.class));
    }

    @Test
    void archiveMissingTeamReturnsNotFound() {
        when(teamMapper.findById(eq(99))).thenReturn(null);

        assertThrows(NoSuchElementException.class, () -> teamService.archiveTeam(99));
        verify(teamMapper, never()).archiveTeam(99);
    }

    private TeamSaveRequest validRequest() {
        TeamSaveRequest request = new TeamSaveRequest();
        request.setYear("2025");
        request.setName("星火小队");
        request.setOwnerUserId(7);
        request.setRegion("甘肃陇南");
        request.setSchool("希望小学");
        request.setDescription("小队简介");
        request.setCoverUrl("/covers/team-10.jpg");
        request.setStatus("DRAFT");
        return request;
    }

    private UserEntity user(int id, int level) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setLevel(level);
        return user;
    }
}
