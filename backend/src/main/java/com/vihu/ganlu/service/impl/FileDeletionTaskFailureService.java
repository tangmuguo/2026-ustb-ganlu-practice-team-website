package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.mappers.FileDeletionTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileDeletionTaskFailureService {
    private final FileDeletionTaskMapper taskMapper;

    public FileDeletionTaskFailureService(FileDeletionTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(long taskId, String error, int previousRetryCount) {
        int exponent = Math.min(Math.max(previousRetryCount, 0), 10);
        long delaySeconds = Math.min(3600L, 30L * (1L << exponent));
        String message = error == null ? "未知删除错误" : error;
        if (message.length() > 1000) message = message.substring(0, 1000);
        taskMapper.markFailure(taskId, message, delaySeconds);
    }
}
