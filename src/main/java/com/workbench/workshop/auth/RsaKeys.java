package com.workbench.workshop.auth;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Lê chaves RSA da configuração: Base64 do DER (pública X.509, privada PKCS#8), numa linha só.
 * Também aceita o PEM inteiro (com {@code -----BEGIN ...-----}), para facilitar copiar e colar.
 */
final class RsaKeys {

    private RsaKeys() {}

    static PublicKey publicKey(String encoded) {
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decode(encoded)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Chave pública RSA inválida (esperado Base64 do DER X.509).", e);
        }
    }

    static PrivateKey privateKey(String encoded) {
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decode(encoded)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Chave privada RSA inválida (esperado Base64 do DER PKCS#8).", e);
        }
    }

    private static byte[] decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("chave vazia");
        }
        String body = encoded.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
