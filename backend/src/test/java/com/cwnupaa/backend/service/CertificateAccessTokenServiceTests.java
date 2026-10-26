package com.cwnupaa.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateAccessTokenServiceTests {
    @Test
    void ticketIsBoundToSessionAndScope() {
        CertificateAccessTokenService service = new CertificateAccessTokenService();
        MockHttpSession session = new MockHttpSession();
        String token = service.issueForUser(session, 42L, 7L);

        assertThat(token).isNotBlank();
        assertThat(service.resolve(session, token, CertificateAccessTokenService.AccessScope.USER))
                .extracting(CertificateAccessTokenService.AccessTicket::grantId,
                        CertificateAccessTokenService.AccessTicket::userId)
                .containsExactly(42L, 7L);
        assertThat(service.resolve(session, token, CertificateAccessTokenService.AccessScope.GUEST)).isNull();
        assertThat(service.resolve(new MockHttpSession(), token, CertificateAccessTokenService.AccessScope.USER)).isNull();
    }

    @Test
    void guestTicketCarriesValidatedIdentity() {
        CertificateAccessTokenService service = new CertificateAccessTokenService();
        MockHttpSession session = new MockHttpSession();
        String token = service.issueForGuest(session, 9L, "示例大学", "20260001", "张三");

        CertificateAccessTokenService.AccessTicket ticket = service.resolve(
                session, token, CertificateAccessTokenService.AccessScope.GUEST);
        assertThat(ticket).isNotNull();
        assertThat(ticket.school()).isEqualTo("示例大学");
        assertThat(ticket.numberId()).isEqualTo("20260001");
        assertThat(ticket.realName()).isEqualTo("张三");
    }
}
