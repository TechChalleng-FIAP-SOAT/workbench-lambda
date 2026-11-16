package com.workbench.workshop.auth;

import com.amazonaws.services.lambda.runtime.events.APIGatewayCustomAuthorizerEvent;
import com.amazonaws.services.lambda.runtime.events.IamPolicyResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenAuthorizerHandlerTest {

    private static final String METHOD_ARN =
            "arn:aws:execute-api:us-east-1:123456789012:abc123/prod/GET/orders";

    private JwtIssuer issuer;
    private TokenAuthorizerHandler handler;

    @BeforeEach
    void setUp() {
        issuer = new JwtIssuer(TestRsa.PLATFORM.getPrivate(), 86_400_000L);
        handler = new TokenAuthorizerHandler(new JwtVerifier(TestRsa.PLATFORM.getPublic()));
    }

    private IamPolicyResponse invoke(String authorizationToken) {
        APIGatewayCustomAuthorizerEvent event = new APIGatewayCustomAuthorizerEvent();
        event.setAuthorizationToken(authorizationToken);
        event.setMethodArn(METHOD_ARN);
        return handler.handleRequest(event, null);
    }

    @Test
    void allowsValidTokenAndExposesClaims() {
        String token = issuer.issue("52998224725", new Client(7L, "JOAO", true));

        IamPolicyResponse response = invoke("Bearer " + token);

        assertThat(response.getPrincipalId()).isEqualTo("52998224725");
        assertThat(response.getContext())
                .containsEntry("clientId", "7")
                .containsEntry("name", "JOAO")
                .containsEntry("cpf", "52998224725");
        // Policy document carries a single Allow statement for the requested method ARN.
        assertThat(response.getPolicyDocument())
                .containsEntry("Version", IamPolicyResponse.VERSION_2012_10_17);
        Object[] statements = (Object[]) response.getPolicyDocument().get("Statement");
        assertThat(statements).hasSize(1);
    }

    @Test
    void scopesAllowPolicyToWholeApiStageSoAuthorizerCacheIsSafe() {
        assertThat(TokenAuthorizerHandler.wildcardResource(
                "arn:aws:execute-api:us-east-1:123456789012:abc123/prod/GET/orders"))
                .isEqualTo("arn:aws:execute-api:us-east-1:123456789012:abc123/prod/*/*");
    }

    @Test
    void acceptsTokenWithoutBearerPrefix() {
        String token = issuer.issue("52998224725", new Client(7L, "JOAO", true));
        assertThat(invoke(token).getPrincipalId()).isEqualTo("52998224725");
    }

    @Test
    void rejectsMissingToken() {
        assertThatThrownBy(() -> invoke(null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Unauthorized");
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String foreign = new JwtIssuer(TestRsa.FOREIGN.getPrivate(), 86_400_000L)
                .issue("52998224725", new Client(7L, "JOAO", true));

        assertThatThrownBy(() -> invoke("Bearer " + foreign))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Unauthorized");
    }

    @Test
    void rejectsExpiredToken() {
        String expired = new JwtIssuer(TestRsa.PLATFORM.getPrivate(), -1000L)
                .issue("52998224725", new Client(7L, "JOAO", true));

        assertThatThrownBy(() -> invoke("Bearer " + expired))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Unauthorized");
    }

    @Test
    void rejectsTokenSignedWithTheOldSharedSecret() {
        // HS256 com o antigo JWT_SECRET compartilhado: não é mais aceito.
        String hs256 = io.jsonwebtoken.Jwts.builder().header().keyId(JwtIssuer.KEY_ID).and()
                .subject("52998224725")
                .expiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "test-secret-that-is-long-enough-for-hs256-signing!!".getBytes()))
                .compact();

        assertThatThrownBy(() -> invoke("Bearer " + hs256))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Unauthorized");
    }
}
