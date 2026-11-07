package com.eudi.verifier.dto.response;

import java.time.Instant;

public record CredentialResponse(
        String credential,
        String format,
        Instant expiresAt
) {}
