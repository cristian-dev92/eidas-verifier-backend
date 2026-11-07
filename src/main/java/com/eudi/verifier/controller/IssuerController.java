package com.eudi.verifier.controller;

import com.eudi.verifier.dto.request.AgeCredentialRequest;
import com.eudi.verifier.dto.response.CredentialResponse;
import com.eudi.verifier.service.IssuerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/issuer")
@Tag(name = "EUDI Age Issuer", description = "Emisor de credenciales de edad (SD-JWT) para la demo")
public class IssuerController {

    private final IssuerService issuerService;

    public IssuerController(IssuerService issuerService) {
        this.issuerService = issuerService;
    }

    @GetMapping(value = "/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Claves públicas (JWKS) del emisor")
    public ResponseEntity<String> getJwks() {
        return ResponseEntity.ok(issuerService.getJwks());
    }

    @PostMapping("/credential")
    @Operation(summary = "Emite una credencial de edad firmada (age_over_15)")
    public ResponseEntity<CredentialResponse> issueCredential(
            @RequestBody(required = false) AgeCredentialRequest request) {
        boolean ageOver15 = request != null && request.ageOver15();
        return ResponseEntity.ok(issuerService.issueAgeCredential(ageOver15));
    }
}
