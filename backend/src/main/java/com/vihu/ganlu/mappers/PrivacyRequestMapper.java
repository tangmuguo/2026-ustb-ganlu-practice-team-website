package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.privacy.PrivacyRequestEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PrivacyRequestMapper {
    int insert(PrivacyRequestEntity request);

    PrivacyRequestEntity findById(@Param("id") long id);

    PrivacyRequestEntity findByIdForUpdate(@Param("id") long id);

    PrivacyRequestEntity findByIdForRequester(@Param("id") long id,
                                               @Param("requesterUserId") int requesterUserId);

    List<PrivacyRequestEntity> findByRequester(@Param("requesterUserId") int requesterUserId,
                                               @Param("offset") int offset,
                                               @Param("limit") int limit);

    int countByRequester(@Param("requesterUserId") int requesterUserId);

    List<PrivacyRequestEntity> findRecent(@Param("status") String status,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    int countRecent(@Param("status") String status);

    int countOpenWithdrawal(@Param("requesterUserId") int requesterUserId,
                            @Param("consentType") String consentType);

    int updateDecision(@Param("id") long id,
                       @Param("actorUserId") int actorUserId,
                       @Param("status") String status,
                       @Param("decisionCode") String decisionCode,
                       @Param("decisionReason") String decisionReason,
                       @Param("retentionDecision") String retentionDecision);
}
