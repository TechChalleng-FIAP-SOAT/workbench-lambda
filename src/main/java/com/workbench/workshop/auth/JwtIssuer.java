package com.workbench.workshop.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * Issues JWTs for authenticated clients.
 * Signs with HS256 using the shared JWT_SECRET so the tokens are verifiable
 * by the same secret configured across the platform (API Gateway authorizer / main app).
 */
public class JwtIssuer {

    private final SecretKey key;
    private final long expirationMillis;

    public JwtIssuer(String secret, long expirationMillis) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
    }

    /** Builds an issuer from environment variables (JWT_SECRET required, JWT_EXPIRATION optional). */
    public static JwtIssuer fromEnv() {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: JWT_SECRET");
        }
        String expiration = System.getenv("JWT_EXPIRATION");
        long expirationMillis = (expiration == null || expiration.isBlank())
                ? 86_400_000L
                : Long.parseLong(expiration);
        return new JwtIssuer(secret, expirationMillis);
    }

    /**
     * @param cpf    normalized CPF digits, used as the token subject
     * @param client the authenticated client
     * @return signed compact JWT
     */
    public String issue(String cpf, Client client) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(cpf)
                .claim("clientId", client.id())
                .claim("name", client.name())
                .claim("roles", List.of("CLIENT"))
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMillis))
                .signWith(key)
                .compact();
    }

    /**
     * Emite um token de STAFF: subject = e-mail, roles = papéis do usuário
     * (ADMIN/TECHNICIAN). Sem a claim {@code clientId} — é assim que os serviços
     * distinguem staff de cliente (o JwtFilter do OS Service só aplica ROLE_CLIENT
     * quando há clientId; caso contrário, usa as roles das claims).
     *
     * @param email subject do token (e-mail do usuário staff)
     * @param roles papéis a incluir na claim {@code roles}
     * @return JWT assinado
     */
    public String issueStaff(String email, java.util.Collection<String> roles) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(email)
                .claim("roles", java.util.List.copyOf(roles))
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMillis))
                .signWith(key)
                .compact();
    }

    public long getExpirationMillis() {
        return expirationMillis;
    }
}
