package com.eudi.verifier.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NonceService {

    private static final int NONCE_BYTE_LENGTH = 24;
    private static final long EXPIRATION_SECONDS = 180; // 3 minutos de validez para el QR
    private final SecureRandom secureRandom = new SecureRandom();

    // Guardado en memoria: nonce -> tiempo de expiración
    private final Map activeNonces = new ConcurrentHashMap<>();

    /**
     * Genera un nonce único de un solo uso con expiración corta.
     */
    public String generateNonce() {
        byte[] randomBytes = new byte[NONCE_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        activeNonces.put(nonce, Instant.now().plusSeconds(EXPIRATION_SECONDS));
        return nonce;
    }

    /**
     * Valida si un nonce existe, es válido y no ha expirado.
     * Si es válido, se ELIMINA inmediatamente (Single Use / Un solo uso).
     */
    public boolean validateAndConsumeNonce(String nonce) {
        if (nonce == null || !activeNonces.containsKey(nonce)) {
            return false;
        }

        Instant expiration = (Instant) activeNonces.get(nonce);
        activeNonces.remove(nonce); // Consumir siempre para prevenir Replay Attacks

        return Instant.now().isBefore(expiration);
    }

    public Instant getExpirationTime(String nonce) {
        return (Instant) Optional.ofNullable(activeNonces.get(nonce)).orElse(Instant.now());
    }
}
