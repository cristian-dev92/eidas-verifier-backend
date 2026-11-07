package com.eudi.verifier.dto.response;

import com.eudi.verifier.model.VerificationStatus;

import java.time.Instant;

public record VerificationStatusResponse(
        String nonce,
        VerificationStatus status,
        boolean isAgeVerified,
        String sessionToken,
        String message,
        String messageCode,
        Instant expiresAt,
        boolean completed
) {}
