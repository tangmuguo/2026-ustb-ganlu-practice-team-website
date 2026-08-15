package com.vihu.ganlu.security.file;

import com.vihu.ganlu.entitys.MediaPrivacyConsentEntity;
import com.vihu.ganlu.mappers.MediaPrivacyConsentMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChildPrivacyGateServiceTests {
    @Test
    void missingConsentIsDeniedByDefault() {
        MediaPrivacyConsentMapper mapper = mock(MediaPrivacyConsentMapper.class);
        when(mapper.findActive(eq("CHILD_PHOTO"), anyLong(), anyInt())).thenReturn(null);
        ChildPrivacyGateService gate = new ChildPrivacyGateService(mapper);

        assertThrows(MissingPrivacyConsentException.class,
                () -> gate.requirePublicationAllowed(PrivacyAssetType.CHILD_PHOTO, 8L, 42, null));
    }

    @Test
    void grantedConsentAllowsPublication() {
        MediaPrivacyConsentMapper mapper = mock(MediaPrivacyConsentMapper.class);
        MediaPrivacyConsentEntity consent = new MediaPrivacyConsentEntity();
        consent.setConsentStatus("GRANTED");
        when(mapper.findActive("CHILD_VIDEO", 9L, 42)).thenReturn(consent);
        ChildPrivacyGateService gate = new ChildPrivacyGateService(mapper);

        assertDoesNotThrow(() -> gate.requirePublicationAllowed(
                PrivacyAssetType.CHILD_VIDEO, 9L, 42, null));
    }

    @Test
    void withdrawnConsentIsDeniedEvenIfARecordExists() {
        MediaPrivacyConsentMapper mapper = mock(MediaPrivacyConsentMapper.class);
        MediaPrivacyConsentEntity consent = new MediaPrivacyConsentEntity();
        consent.setConsentStatus("GRANTED");
        consent.setWithdrawnAt(new java.util.Date());
        when(mapper.findActive("CLASSROOM_LOG", 10L, 42)).thenReturn(consent);
        ChildPrivacyGateService gate = new ChildPrivacyGateService(mapper);

        assertThrows(MissingPrivacyConsentException.class,
                () -> gate.requirePublicationAllowed(PrivacyAssetType.CLASSROOM_LOG, 10L, 42, null));
    }
}
