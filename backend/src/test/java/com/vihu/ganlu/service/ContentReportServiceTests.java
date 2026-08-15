package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.ContentReportEntity;
import com.vihu.ganlu.entitys.MessageEntity;
import com.vihu.ganlu.entitys.ReplyEntity;
import com.vihu.ganlu.entitys.TeamEntity;
import com.vihu.ganlu.entitys.TeamMediaEntity;
import com.vihu.ganlu.entitys.TeamPageImageEntity;
import com.vihu.ganlu.entitys.TeamPageWordEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.report.ContentReportCreateRequest;
import com.vihu.ganlu.entitys.report.ContentReportResolutionRequest;
import com.vihu.ganlu.mappers.ContentReportMapper;
import com.vihu.ganlu.mappers.MessageMapper;
import com.vihu.ganlu.mappers.ReplyMapper;
import com.vihu.ganlu.mappers.TeamMapper;
import com.vihu.ganlu.mappers.TeamMediaMapper;
import com.vihu.ganlu.mappers.TeamPageImageMapper;
import com.vihu.ganlu.mappers.TeamPageWordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentReportServiceTests {
    private ContentReportMapper reportMapper;
    private MessageMapper messageMapper;
    private ReplyMapper replyMapper;
    private TeamMapper teamMapper;
    private TeamPageImageMapper imageMapper;
    private TeamPageWordMapper wordMapper;
    private TeamMediaMapper mediaMapper;
    private ContentReportService service;

    @BeforeEach
    void setUp() {
        reportMapper = mock(ContentReportMapper.class);
        messageMapper = mock(MessageMapper.class);
        replyMapper = mock(ReplyMapper.class);
        teamMapper = mock(TeamMapper.class);
        imageMapper = mock(TeamPageImageMapper.class);
        wordMapper = mock(TeamPageWordMapper.class);
        mediaMapper = mock(TeamMediaMapper.class);
        service = new ContentReportService(reportMapper, messageMapper, replyMapper,
                teamMapper, imageMapper, wordMapper, mediaMapper);
        when(reportMapper.insert(any(ContentReportEntity.class))).thenAnswer(invocation -> {
            ContentReportEntity report = invocation.getArgument(0);
            report.setId(101L);
            return 1;
        });
    }

    @Test
    void anonymousReporterCanReportPublishedTeamImageWithoutContactData() {
        when(imageMapper.findById(7)).thenReturn(image(7, 11, "PUBLISHED"));
        when(teamMapper.findById(11)).thenReturn(team(11, TeamEntity.Status.PUBLISHED));

        long id = service.create(request("TEAM_IMAGE", 7), null);

        assertEquals(101L, id);
        org.mockito.ArgumentCaptor<ContentReportEntity> captor =
                org.mockito.ArgumentCaptor.forClass(ContentReportEntity.class);
        verify(reportMapper).insert(captor.capture());
        assertNull(captor.getValue().getReporterUserId());
        assertEquals("TEAM_IMAGE", captor.getValue().getTargetType());
        assertEquals(7, captor.getValue().getTargetId());
    }

    @Test
    void loggedInReporterStoresOnlyInternalUserId() {
        when(wordMapper.findById(8)).thenReturn(word(8, 11, "PUBLISHED"));
        when(teamMapper.findById(11)).thenReturn(team(11, TeamEntity.Status.PUBLISHED));
        UserEntity reporter = user(22, 2);

        service.create(request("TEAM_WORD", 8), reporter);

        org.mockito.ArgumentCaptor<ContentReportEntity> captor =
                org.mockito.ArgumentCaptor.forClass(ContentReportEntity.class);
        verify(reportMapper).insert(captor.capture());
        assertEquals(22, captor.getValue().getReporterUserId());
        assertNull(captor.getValue().getDescription());
    }

    @Test
    void draftOrArchivedTeamContentCannotBeReported() {
        when(imageMapper.findById(7)).thenReturn(image(7, 11, "PUBLISHED"));
        when(teamMapper.findById(11)).thenReturn(team(11, TeamEntity.Status.DRAFT));

        assertThrows(NoSuchElementException.class, () -> service.create(request("TEAM_IMAGE", 7), null));

        when(imageMapper.findById(9)).thenReturn(image(9, 11, "ARCHIVED"));
        when(teamMapper.findById(11)).thenReturn(team(11, TeamEntity.Status.PUBLISHED));
        assertThrows(NoSuchElementException.class, () -> service.create(request("TEAM_IMAGE", 9), null));
        verify(reportMapper, never()).insert(any(ContentReportEntity.class));
    }

    @Test
    void targetTypeMustResolveAgainstItsOwnTable() {
        // The same numeric id being present in the image table does not make it a
        // valid TEAM_WORD target; the requested type selects the authoritative table.
        when(imageMapper.findById(12)).thenReturn(image(12, 11, "PUBLISHED"));
        when(wordMapper.findById(12)).thenReturn(null);
        when(teamMapper.findById(11)).thenReturn(team(11, TeamEntity.Status.PUBLISHED));

        assertThrows(NoSuchElementException.class, () -> service.create(request("TEAM_WORD", 12), null));
        verify(reportMapper, never()).insert(any(ContentReportEntity.class));
    }

    @Test
    void mediaRequiresPublishedTeamAndPublishedSameTeamParent() {
        TeamMediaEntity media = media(31, 11, "PUBLISHED", "IMAGE", 41);
        when(mediaMapper.findById(31)).thenReturn(media);
        when(teamMapper.findById(11)).thenReturn(team(11, TeamEntity.Status.PUBLISHED));
        when(imageMapper.findById(41)).thenReturn(image(41, 12, "PUBLISHED"));

        assertThrows(NoSuchElementException.class, () -> service.create(request("TEAM_MEDIA", 31), null));

        when(imageMapper.findById(41)).thenReturn(image(41, 11, "ARCHIVED"));
        assertThrows(NoSuchElementException.class, () -> service.create(request("TEAM_MEDIA", 31), null));
        verify(reportMapper, never()).insert(any(ContentReportEntity.class));
    }

    @Test
    void independentPublishedMediaCanBeReported() {
        when(mediaMapper.findById(32)).thenReturn(media(32, 11, "PUBLISHED", null, null));
        when(teamMapper.findById(11)).thenReturn(team(11, TeamEntity.Status.PUBLISHED));

        assertEquals(101L, service.create(request("TEAM_MEDIA", 32), null));
        verify(reportMapper).insert(any(ContentReportEntity.class));
    }

    @Test
    void existingMessageAndReplyReportsStillRequirePublicProjection() {
        MessageEntity message = new MessageEntity();
        message.setId(51);
        ReplyEntity reply = new ReplyEntity();
        reply.setId(52);
        when(messageMapper.selectMessageById(51)).thenReturn(message);
        when(replyMapper.selectReplyById(52)).thenReturn(reply);

        assertEquals(101L, service.create(request("MESSAGE", 51), null));
        assertEquals(101L, service.create(request("REPLY", 52), null));
        verify(messageMapper).selectMessageById(51);
        verify(replyMapper).selectReplyById(52);
    }

    @Test
    void administratorResolutionNeedsValidReasonCodeAndRole() {
        UserEntity admin = user(1, 0);
        ContentReportResolutionRequest resolution = new ContentReportResolutionRequest();
        resolution.setStatus("RESOLVED");
        resolution.setResolutionCode("CONTENT_REMOVED");
        resolution.setResolutionNote("Policy violation confirmed");
        when(reportMapper.updateResolution(101L, 1, "RESOLVED", "CONTENT_REMOVED",
                "Policy violation confirmed")).thenReturn(1);

        service.resolve(101L, resolution, admin);
        verify(reportMapper).updateResolution(101L, 1, "RESOLVED", "CONTENT_REMOVED",
                "Policy violation confirmed");

        assertThrows(SecurityException.class, () -> service.resolve(101L, resolution, user(2, 1)));
        ContentReportResolutionRequest missingReason = new ContentReportResolutionRequest();
        missingReason.setStatus("RESOLVED");
        assertThrows(IllegalArgumentException.class, () -> service.resolve(101L, missingReason, admin));
    }

    private ContentReportCreateRequest request(String targetType, int targetId) {
        ContentReportCreateRequest request = new ContentReportCreateRequest();
        request.setTargetType(targetType);
        request.setTargetId(targetId);
        request.setCategory("OTHER");
        return request;
    }

    private TeamPageImageEntity image(int id, int teamId, String status) {
        TeamPageImageEntity image = new TeamPageImageEntity();
        image.setId(id);
        image.setTeamId(teamId);
        image.setStatus(status);
        return image;
    }

    private TeamPageWordEntity word(int id, int teamId, String status) {
        TeamPageWordEntity word = new TeamPageWordEntity();
        word.setId(id);
        word.setTeamId(teamId);
        word.setStatus(status);
        return word;
    }

    private TeamMediaEntity media(int id, int teamId, String status, String relatedType, Integer relatedId) {
        TeamMediaEntity media = new TeamMediaEntity();
        media.setId(id);
        media.setTeamId(teamId);
        media.setStatus(status);
        media.setRelatedType(relatedType);
        media.setRelatedId(relatedId);
        return media;
    }

    private TeamEntity team(int id, TeamEntity.Status status) {
        TeamEntity team = new TeamEntity();
        team.setId(id);
        team.setStatus(status);
        return team;
    }

    private UserEntity user(int id, int level) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setLevel(level);
        return user;
    }
}
