package com.cwnupaa.backend.service;

import com.cwnupaa.backend.entity.CertificateGrant;
import com.cwnupaa.backend.entity.CertificateLevel;
import com.cwnupaa.backend.entity.CertificateType;
import com.cwnupaa.backend.entity.RatingComposition;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RatingServiceCertificateTests {

    @Test
    void skipsOfficialCertificateWhenTemplateDisablesRating() {
        assertThat(calculateOfficialCertificateScore(false)).isZero();
    }

    @Test
    void includesLegacyTemplateWhenRatingSettingIsMissing() {
        assertThat(calculateOfficialCertificateScore(null)).isEqualTo(100);
    }

    private int calculateOfficialCertificateScore(Boolean ratingEnabled) {
        long termId = 1L;
        long userId = 2L;
        long certificateTypeId = 3L;
        long levelId = 4L;

        CertificateType type = new CertificateType();
        type.setCertificateTypeId(certificateTypeId);
        type.setTypeId(levelId);
        type.setRatingEnabled(ratingEnabled);

        CertificateGrant grant = new CertificateGrant();
        grant.setCertificateTypeId(certificateTypeId);
        grant.setUserId(userId);

        CertificateLevel level = new CertificateLevel();
        level.setLevelId(levelId);
        level.setBaseRating(100);

        RatingComposition rating = new RatingComposition();
        rating.setUserId(userId);
        rating.setRatingCertificate(0);

        RatingService service = new RatingService();
        ReflectionTestUtils.invokeMethod(service, "calculateCertificateScoreInTerm",
                termId,
                Map.of(userId, rating),
                Map.of(termId, List.of(grant)),
                Collections.emptyMap(),
                Map.of(levelId, 100),
                Map.of(levelId, level),
                List.of(level),
                Map.of(certificateTypeId, type),
                Collections.emptyMap());

        return rating.getRatingCertificate();
    }
}
