package com.workbench.workshop.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.ProtectedHeader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtIssuerTest {

    @Test
    void issuesTokenWithExpectedClaimsAndSignature() {
        JwtIssuer issuer = new JwtIssuer(TestRsa.PLATFORM.getPrivate(), 86_400_000L);
        Client client = new Client(42L, "JOAO DA SILVA", true);

        String token = issuer.issue("52998224725", client);

        var jws = Jwts.parser().verifyWith(TestRsa.PLATFORM.getPublic()).build().parseSignedClaims(token);
        Claims claims = jws.getPayload();

        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("RS256");
        assertThat(((ProtectedHeader) jws.getHeader()).getKeyId()).isEqualTo(JwtIssuer.KEY_ID);

        assertThat(claims.getSubject()).isEqualTo("52998224725");
        assertThat(claims.get("clientId", Long.class)).isEqualTo(42L);
        assertThat(claims.get("name", String.class)).isEqualTo("JOAO DA SILVA");
        assertThat(claims.get("roles", List.class)).containsExactly("CLIENT");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void issuesShortLivedServiceTokenForCustomerLookup() {
        JwtIssuer issuer = new JwtIssuer(TestRsa.PLATFORM.getPrivate(), 86_400_000L);

        String token = issuer.issueService();

        var jws = Jwts.parser().verifyWith(TestRsa.PLATFORM.getPublic()).build().parseSignedClaims(token);
        Claims claims = jws.getPayload();
        assertThat(((ProtectedHeader) jws.getHeader()).getKeyId()).isEqualTo(JwtIssuer.KEY_ID);
        assertThat(claims.getSubject()).isEqualTo(JwtIssuer.SERVICE_SUBJECT);
        assertThat(claims.get("roles", List.class)).containsExactly("TECHNICIAN");
        assertThat(claims.get("clientId")).isNull();
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
                .isEqualTo(JwtIssuer.SERVICE_TOKEN_MILLIS);
    }
}
