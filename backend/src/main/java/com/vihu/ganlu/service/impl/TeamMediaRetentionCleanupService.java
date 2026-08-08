package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.mappers.TeamMediaMapper;
import com.vihu.ganlu.service.TeamMediaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TeamMediaRetentionCleanupService {
    private final TeamMediaMapper mediaMapper;
    private final TeamMediaService mediaService;
    private final int retentionDays;

    public TeamMediaRetentionCleanupService(
            TeamMediaMapper mediaMapper,
            TeamMediaService mediaService,
            @Value("${team.media.archived-retention-days:30}") int retentionDays) {
        this.mediaMapper = mediaMapper;
        this.mediaService = mediaService;
        this.retentionDays = Math.max(1, retentionDays);
    }

    @Scheduled(fixedDelayString = "${team.media.retention-cleanup-interval-ms:3600000}")
    public void enqueueExpiredArchivedMedia() {
        for (Integer id : mediaMapper.findArchivedIdsForRetention(retentionDays, 50)) {
            try {
                mediaService.purgeById(id);
            } catch (RuntimeException error) {
                log.warn("归档附件进入删除队列失败: mediaId={}", id, error);
            }
        }
    }
}
