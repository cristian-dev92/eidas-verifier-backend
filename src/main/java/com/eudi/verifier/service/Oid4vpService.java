package com.eudi.verifier.service;

import com.eudi.verifier.dto.request.PresentationSubmissionRequest;
import com.eudi.verifier.dto.response.ChallengeResponse;
import com.eudi.verifier.dto.response.VerificationResponse;
import com.eudi.verifier.dto.response.VerificationStatusResponse;
import com.eudi.verifier.model.VerificationLog;
import com.eudi.verifier.model.VerificationStatus;
import com.eudi.verifier.repository.VerificationLogRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class Oid4vpService {

    private static final String MSG_CODE_SUCCESS = "verification.success";
    private static final String MSG_CODE_NONCE_INVALID = "verification.nonceInvalid";
    private static final String MSG_CODE_INVALID_PROOF = "verification.invalidProof";
    private static final String MSG_CODE_AGE_NOT_MET = "verification.ageNotMet";
    private static final String MSG_CODE_PENDING = "verification.pending";

    private final NonceService nonceService;
    private final VerificationLogRepository verificationLogRepository;
    private final SdJwtVerifierService sdJwtVerifierService;
    private final String clientId;
    private final String scope;

    public Oid4vpService(NonceService nonceService,
                         VerificationLogRepository verificationLogRepository,
                         SdJwtVerifierService sdJwtVerifierService,
                         @Value("${app.verifier.client-id:https://eudi-verifier.com}") String clientId,
                         @Value("${app.verifier.scope:age_over_15}") String scope) {
        this.nonceService = nonceService;
        this.verificationLogRepository = verificationLogRepository;
        this.sdJwtVerifierService = sdJwtVerifierService;
        this.clientId = clientId;
        this.scope = scope;
    }

    /**
     * Genera la Petición de Presentación según el esquema OpenID4VP y persiste el challenge como PENDING.
     */
    @Transactional
    public ChallengeResponse createVerificationChallenge() {
        String nonce = nonceService.generateNonce();
        Instant expiresAt = nonceService.getExpirationTime(nonce);

        String presentationUri = String.format(
                "openid4vp://verify?response_type=vp_token&client_id=%s&nonce=%s&scope=%s",
                clientId, nonce, scope
        );

        verificationLogRepository.save(new VerificationLog(
                nonce,
                VerificationStatus.PENDING,
                false,
                clientId,
                null,
                expiresAt
        ));

        return new ChallengeResponse(nonce, presentationUri, expiresAt);
    }

    /**
     * Procesa la prueba Verifiable Presentation (VP) enviada por el monedero digital.
     */
    @Transactional
    public VerificationResponse verifyPresentation(@Valid PresentationSubmissionRequest request) {
        VerificationLog log = verificationLogRepository
                .findFirstByNonceOrderByCreatedAtDesc(request.nonce())
                .orElse(null);

        // 1. El challenge debe existir y seguir pendiente (single use / anti-replay)
        if (log == null || log.getStatus() != VerificationStatus.PENDING) {
            return expiredResponse();
        }

        // 2. Comprobar expiración del challenge
        if (log.getExpiresAt() != null && Instant.now().isAfter(log.getExpiresAt())) {
            log.setStatus(VerificationStatus.EXPIRED);
            log.setErrorMessage("El nonce es inválido o ha expirado.");
            verificationLogRepository.save(log);
            return expiredResponse();
        }

        // 3. Validación criptográfica real del SD-JWT
        SdJwtVerificationResult verification = sdJwtVerifierService.verifySdJwt(request.vpToken());

        // 3.a. Credencial válida pero el usuario no cumple la edad
        if (verification == SdJwtVerificationResult.AGE_NOT_MET) {
            log.setStatus(VerificationStatus.REJECTED);
            log.setAgeVerified(false);
            log.setErrorMessage("El usuario no cumple el requisito de edad.");
            verificationLogRepository.save(log);

            return new VerificationResponse(
                    VerificationStatus.REJECTED,
                    false,
                    null,
                    "El usuario no cumple el requisito de edad.",
                    MSG_CODE_AGE_NOT_MET
            );
        }

        // 3.b. Firma/token inválido
        if (verification != SdJwtVerificationResult.VALID) {
            log.setStatus(VerificationStatus.INVALID_PROOF);
            log.setAgeVerified(false);
            log.setErrorMessage("La firma digital o prueba Verifiable Presentation es inválida.");
            verificationLogRepository.save(log);

            return new VerificationResponse(
                    VerificationStatus.INVALID_PROOF,
                    false,
                    null,
                    "La firma digital o prueba Verifiable Presentation es inválida.",
                    MSG_CODE_INVALID_PROOF
            );
        }

        // 4. Verificación exitosa
        String sessionToken = "eudi_sess_" + UUID.randomUUID();
        log.setStatus(VerificationStatus.SUCCESS);
        log.setAgeVerified(true);
        log.setSessionToken(sessionToken);
        log.setErrorMessage(null);
        verificationLogRepository.save(log);

        return new VerificationResponse(
                VerificationStatus.SUCCESS,
                true,
                sessionToken,
                "Verificación de edad (>15 años) completada con éxito.",
                MSG_CODE_SUCCESS
        );
    }

    /**
     * Consulta el estado de un challenge para que el panel pueda hacer polling tras el escaneo del QR.
     */
    @Transactional
    public VerificationStatusResponse getStatus(String nonce) {
        VerificationLog log = verificationLogRepository
                .findFirstByNonceOrderByCreatedAtDesc(nonce)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge no encontrado"));

        if (log.getStatus() == VerificationStatus.PENDING
                && log.getExpiresAt() != null
                && Instant.now().isAfter(log.getExpiresAt())) {
            log.setStatus(VerificationStatus.EXPIRED);
            log.setErrorMessage("El nonce es inválido o ha expirado.");
            verificationLogRepository.save(log);
        }

        boolean completed = log.getStatus() != VerificationStatus.PENDING;
        String messageCode;
        String message;

        switch (log.getStatus()) {
            case SUCCESS -> {
                messageCode = MSG_CODE_SUCCESS;
                message = "Verificación de edad (>15 años) completada con éxito.";
            }
            case INVALID_PROOF -> {
                messageCode = MSG_CODE_INVALID_PROOF;
                message = "La firma digital o prueba Verifiable Presentation es inválida.";
            }
            case EXPIRED -> {
                messageCode = MSG_CODE_NONCE_INVALID;
                message = "El nonce es inválido o ha expirado.";
            }
            case REJECTED -> {
                messageCode = MSG_CODE_AGE_NOT_MET;
                message = "El usuario no cumple el requisito de edad.";
            }
            default -> {
                messageCode = MSG_CODE_PENDING;
                message = "Esperando la respuesta del monedero...";
            }
        }

        return new VerificationStatusResponse(
                nonce,
                log.getStatus(),
                log.isAgeVerified(),
                log.getSessionToken(),
                message,
                messageCode,
                log.getExpiresAt(),
                completed
        );
    }

    private VerificationResponse expiredResponse() {
        return new VerificationResponse(
                VerificationStatus.EXPIRED,
                false,
                null,
                "El nonce es inválido o ha expirado.",
                MSG_CODE_NONCE_INVALID
        );
    }
}
