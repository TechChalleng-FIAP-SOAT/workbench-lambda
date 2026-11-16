package com.workbench.workshop.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.ProtectedHeader;
import io.jsonwebtoken.UnsupportedJwtException;

import java.security.PublicKey;

/**
 * Verifies JWTs signed by {@link JwtIssuer} (RS256) with the platform PUBLIC key
 * (JWT_PUBLIC_KEY): the authorizer cannot mint tokens. Only {@code kid} {@value JwtIssuer#KEY_ID}
 * is accepted. Signature and expiration are checked by the jjwt parser; an invalid or expired
 * token throws {@link JwtException}.
 */
public class JwtVerifier {

    private final JwtParser parser;

    public JwtVerifier(PublicKey key) {
        this.parser = Jwts.parser()
                .keyLocator(header -> {
                    String kid = header instanceof ProtectedHeader protectedHeader ? protectedHeader.getKeyId() : null;
                    if (!JwtIssuer.KEY_ID.equals(kid)) {
                        throw new UnsupportedJwtException("Untrusted token key: kid=" + kid);
                    }
                    return key;
                })
                .build();
    }

    /** Builds a verifier from the JWT_PUBLIC_KEY environment variable. */
    public static JwtVerifier fromEnv() {
        String publicKey = System.getenv("JWT_PUBLIC_KEY");
        if (publicKey == null || publicKey.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: JWT_PUBLIC_KEY");
        }
        return new JwtVerifier(RsaKeys.publicKey(publicKey));
    }

    /**
     * @param token compact JWT (without the "Bearer " prefix)
     * @return the token claims when valid
     * @throws JwtException when the signature is invalid or the token is expired/malformed
     */
    public Claims verify(String token) {
        return parser.parseSignedClaims(token).getPayload();
    }
}
