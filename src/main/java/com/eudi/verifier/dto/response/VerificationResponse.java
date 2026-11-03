package com.eudi.verifier.dto.response;


import com.eudi.verifier.model.VerificationStatus;

public record VerificationResponse(
        VerificationStatus status,
        boolean isAgeVerified,
        String sessionToken,
        String message,
        String messageCode
) {}
