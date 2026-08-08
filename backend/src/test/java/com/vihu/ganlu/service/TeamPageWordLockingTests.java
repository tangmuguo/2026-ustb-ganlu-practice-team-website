package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.TeamPageWordEntity;
import com.vihu.ganlu.mappers.TeamMediaMapper;
import com.vihu.ganlu.mappers.TeamPageWordMapper;
import com.vihu.ganlu.service.impl.TeamPageWordServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class TeamPageWordLockingTests {
    @Test
    void archiveLocksParentBeforeCascadingToAttachments() {
        TeamPageWordMapper words = mock(TeamPageWordMapper.class);
        TeamMediaMapper media = mock(TeamMediaMapper.class);
        TeamPageWordEntity parent = new TeamPageWordEntity();
        parent.setId(20);
        parent.setTeamId(9);
        parent.setStatus("PUBLISHED");
        when(words.findByIdForUpdate(20)).thenReturn(parent);
        when(words.archiveById(20)).thenReturn(1);
        TeamPageWordServiceImpl service = new TeamPageWordServiceImpl();
        ReflectionTestUtils.setField(service, "teamPageWordMapper", words);
        ReflectionTestUtils.setField(service, "teamMediaMapper", media);

        assertTrue(service.archiveById(20));

        org.mockito.InOrder order = inOrder(words, media);
        order.verify(words).findByIdForUpdate(20);
        order.verify(words).archiveById(20);
        order.verify(media).archiveByRelated("WORD", 20, 9);
        verify(words, never()).findById(20);
    }
}
