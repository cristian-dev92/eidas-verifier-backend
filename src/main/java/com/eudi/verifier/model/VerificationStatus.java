package com.eudi.verifier.model;

public enum VerificationStatus {
    PENDING,    // El código QR / challenge ha sido generado y espera al monedero
    SUCCESS,    // La credencial se ha validado correctamente (ej. > 15 años)
    REJECTED,   // El usuario no cumple el requisito de edad
    EXPIRED,    // El nonce superó el tiempo límite (TTL)
    INVALID_PROOF // La firma digital o el token de presentación es inválido
}