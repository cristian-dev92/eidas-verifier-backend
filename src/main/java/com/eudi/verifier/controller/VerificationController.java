package com.eudi.verifier.controller;

import com.eudi.verifier.dto.request.PresentationSubmissionRequest;
import com.eudi.verifier.dto.response.ChallengeResponse;
import com.eudi.verifier.dto.response.VerificationResponse;
import com.eudi.verifier.service.Oid4vpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/verify")
@Tag(name = "EUDI Age Verification", description = "Endpoints para la verificación de edad con EUDI Wallet")
public class VerificationController {

    private final Oid4vpService oid4vpService;

    public VerificationController(Oid4vpService oid4vpService) {
        this.oid4vpService = oid4vpService;
    }

    @PostMapping("/challenge")
    @Operation(summary = "Crear un desafío (QR) para iniciar la verificación de edad")
    public ResponseEntity createChallenge() {
        ChallengeResponse challenge = oid4vpService.createVerificationChallenge();
        return ResponseEntity.ok(challenge);
    }

    @PostMapping("/submission")
    @Operation(summary = "Validar el token enviado por la app EUDI Wallet")
    public ResponseEntity submitPresentation(@Valid @RequestBody PresentationSubmissionRequest request) {
        VerificationResponse response = oid4vpService.verifyPresentation(request);
        return ResponseEntity.ok(response);
    }
}