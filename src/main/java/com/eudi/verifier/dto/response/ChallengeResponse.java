package com.eudi.verifier.dto.response;

import java.time.Instant;

public record ChallengeResponse(
        String nonce,
        String presentationUri,
        Instant expiresAt
) {}
