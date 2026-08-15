package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.MediaPrivacyConsentEntity;
import org.apache.ibatis.annotations.Param;

public interface MediaPrivacyConsentMapper {
    MediaPrivacyConsentEntity findActive(
            @Param("assetType") String assetType,
            @Param("assetId") Long assetId,
            @Param("subjectUserId") Integer subjectUserId);

    int insertConsent(MediaPrivacyConsentEntity consent);

    int withdrawConsent(@Param("id") long id, @Param("operatorUserId") int operatorUserId);

    /** Invalidates every currently granted media consent for a subject. */
    int withdrawAllForSubject(@Param("subjectUserId") int subjectUserId,
                              @Param("operatorUserId") int operatorUserId);
}
