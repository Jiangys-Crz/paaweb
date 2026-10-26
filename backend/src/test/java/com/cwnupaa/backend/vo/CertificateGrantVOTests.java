package com.cwnupaa.backend.vo;

import com.cwnupaa.backend.entity.CertificateType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateGrantVOTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exposesRatingSettingForAdminTemplateButNotRecipientCertificate() throws Exception {
        CertificateType adminTemplate = new CertificateType();
        adminTemplate.setRatingEnabled(false);
        assertThat(objectMapper.writeValueAsString(adminTemplate)).contains("\"ratingEnabled\":false");

        CertificateGrantVO recipientCertificate = new CertificateGrantVO();
        recipientCertificate.setRatingEnabled(false);
        assertThat(objectMapper.writeValueAsString(recipientCertificate)).doesNotContain("ratingEnabled");
    }
}
