package com.workbench.workshop.auth;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

/** Par de chaves RSA gerado uma vez por execução dos testes (nada fica no repositório). */
final class TestRsa {

    static final KeyPair PLATFORM = generate();
    static final KeyPair FOREIGN = generate();

    private TestRsa() {}

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
