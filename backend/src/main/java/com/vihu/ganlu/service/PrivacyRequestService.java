package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.privacy.PrivacyRequestCreateRequest;
import com.vihu.ganlu.entitys.privacy.PrivacyRequestResolutionRequest;
import com.vihu.ganlu.entitys.privacy.PrivacyRequestViewDto;

import java.util.List;

/** Privacy-rights ticket workflow. */
public interface PrivacyRequestService {
    long create(PrivacyRequestCreateRequest request, UserEntity actor);

    PrivacyRequestViewDto findMineById(long requestId, UserEntity actor);

    List<PrivacyRequestViewDto> findMine(int page, int pageSize, UserEntity actor);

    int countMine(UserEntity actor);

    PrivacyRequestViewDto findForAdministrator(long requestId, UserEntity actor);

    List<PrivacyRequestViewDto> findRecent(String status, int page, int pageSize, UserEntity actor);

    int countRecent(String status, UserEntity actor);

    void process(long requestId, PrivacyRequestResolutionRequest resolution, UserEntity actor);
}
