package com.eudi.verifier.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class NonceService {

    private static final int NONCE_BYTE_LENGTH = 24;
    private final SecureRandom secureRandom = new SecureRandom();

    private final long expirationSeconds;

    public NonceService(@Value("${app.verifier.nonce-ttl-seconds:180}") long expirationSeconds) {
        this.expirationSeconds = expirationSeconds;
    }

    /**
     * Genera un nonce único de un solo uso.
     * La validez y el consumo se controlan en base de datos mediante el estado del challenge.
     */
    public String generateNonce() {
        byte[] randomBytes = new byte[NONCE_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Tiempo de expiración de un challenge generado en este instante.
     */
    public Instant getExpirationTime(String nonce) {
        return Instant.now().plusSeconds(expirationSeconds);
    }
}
