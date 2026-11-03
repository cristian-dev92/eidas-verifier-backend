package com.eudi.verifier.service;

import com.eudi.verifier.dto.request.PresentationSubmissionRequest;
import com.eudi.verifier.dto.response.ChallengeResponse;
import com.eudi.verifier.dto.response.VerificationResponse;
import com.eudi.verifier.model.VerificationLog;
import com.eudi.verifier.model.VerificationStatus;
import com.eudi.verifier.repository.VerificationLogRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Service
public class Oid4vpService {

    private final NonceService nonceService;
    private final VerificationLogRepository verificationLogRepository;
    private final SdJwtVerifierService sdJwtVerifierService;

    public Oid4vpService(NonceService nonceService,
                         VerificationLogRepository verificationLogRepository,
                         SdJwtVerifierService sdJwtVerifierService) {
        this.nonceService = nonceService;
        this.verificationLogRepository = verificationLogRepository;
        this.sdJwtVerifierService = sdJwtVerifierService;
    }

    /**
     * Genera la Petición de Presentación según el esquema OpenID4VP.
     */
    public ChallengeResponse createVerificationChallenge() {
        String nonce = nonceService.generateNonce();
        Instant expiresAt = nonceService.getExpirationTime(nonce);

        String presentationUri = String.format(
                "openid4vp://verify?response_type=vp_token&client_id=https://eudi-verifier.com&nonce=%s&scope=age_over_15",
                nonce
        );

        return new ChallengeResponse(nonce, presentationUri, expiresAt);
    }

    /**
     * Procesa la prueba Verifiable Presentation (VP) enviada por el monedero digital.
     */
    @Transactional
    public VerificationResponse verifyPresentation(@Valid PresentationSubmissionRequest request) {
        String clientId = "demo-client-app";

        // 1. Validar y consumir el nonce de un solo uso
        boolean isNonceValid = nonceService.validateAndConsumeNonce(request.nonce());
        if (!isNonceValid) {
            // Guardar registro de auditoría (Expirado / No válido)
            verificationLogRepository.save(new VerificationLog(
                    request.nonce(),
                    VerificationStatus.EXPIRED,
                    false,
                    clientId,
                    "El nonce es inválido o ha expirado."
            ));

            return new VerificationResponse(
                    VerificationStatus.EXPIRED,
                    false,
                    null,
                    "El nonce es inválido o ha expirado.",
                    "verification.nonceInvalid"
            );
        }

        // 2. Validación Criptográfica real del SD-JWT
        boolean isValidProof = sdJwtVerifierService.verifySdJwtProof(request.vpToken());

        if (!isValidProof) {
            // Guardar registro de auditoría (Prueba inválida)
            verificationLogRepository.save(new VerificationLog(
                    request.nonce(),
                    VerificationStatus.INVALID_PROOF,
                    false,
                    clientId,
                    "La firma digital o prueba Verifiable Presentation es inválida."
            ));

            return new VerificationResponse(
                    VerificationStatus.INVALID_PROOF,
                    false,
                    null,
                    "La firma digital o prueba Verifiable Presentation es inválida.",
                    "verification.invalidProof"
            );
        }

        // 3. Verificación Exitosa: Guardar registro de auditoría (Éxito)
        verificationLogRepository.save(new VerificationLog(
                request.nonce(),
                VerificationStatus.SUCCESS,
                true,
                clientId,
                null
        ));

        // 4. Generar token de sesión para el usuario verificado
        String sessionToken = "eudi_sess_" + UUID.randomUUID();

        return new VerificationResponse(
                VerificationStatus.SUCCESS,
                true,
                sessionToken,
                "Verificación de edad (>15 años) completada con éxito.",
                "verification.success"
        );
    }
}