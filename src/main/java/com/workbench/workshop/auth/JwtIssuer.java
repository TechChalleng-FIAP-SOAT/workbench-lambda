package com.workbench.workshop.auth;

import io.jsonwebtoken.Jwts;

import java.security.PrivateKey;
import java.util.Date;
import java.util.List;

/**
 * Issues JWTs for authenticated clients and staff.
 * Signs with RS256 using the platform PRIVATE key (JWT_PRIVATE_KEY, only here). The authorizer
 * and the microservices hold just the public key: they can verify tokens but cannot mint them.
 * The {@code kid} header ({@value #KEY_ID}) tells verifiers which public key to use.
 */
public class JwtIssuer {

    /** Key id of the authentication key (same value the microservices trust). */
    public static final String KEY_ID = "workbench-auth";

    private final PrivateKey key;
    private final long expirationMillis;

    public JwtIssuer(PrivateKey key, long expirationMillis) {
        this.key = key;
        this.expirationMillis = expirationMillis;
    }

    /** Builds an issuer from environment variables (JWT_PRIVATE_KEY required, JWT_EXPIRATION optional). */
    public static JwtIssuer fromEnv() {
        String privateKey = System.getenv("JWT_PRIVATE_KEY");
        if (privateKey == null || privateKey.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: JWT_PRIVATE_KEY");
        }
        String expiration = System.getenv("JWT_EXPIRATION");
        long expirationMillis = (expiration == null || expiration.isBlank())
                ? 86_400_000L
                : Long.parseLong(expiration);
        return new JwtIssuer(RsaKeys.privateKey(privateKey), expirationMillis);
    }

    /**
     * @param cpf    normalized CPF digits, used as the token subject
     * @param client the authenticated client
     * @return signed compact JWT
     */
    public String issue(String cpf, Client client) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .header().keyId(KEY_ID).and()
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
                .header().keyId(KEY_ID).and()
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
