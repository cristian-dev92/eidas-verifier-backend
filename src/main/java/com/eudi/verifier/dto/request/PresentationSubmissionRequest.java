package com.eudi.verifier.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PresentationSubmissionRequest(
        @NotBlank(message = "El nonce es obligatorio")
        String nonce,

        @NotBlank(message = "El token de presentación (VP) es obligatorio")
        String vpToken
) {}
