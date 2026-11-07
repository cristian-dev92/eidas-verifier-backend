package com.eudi.verifier.service;

public enum SdJwtVerificationResult {
    VALID,          // Firma correcta y age_over_15 = true
    AGE_NOT_MET,    // Firma correcta pero el usuario no cumple la edad
    INVALID_PROOF   // Token malformado, emisor no confiable o firma inválida
}
