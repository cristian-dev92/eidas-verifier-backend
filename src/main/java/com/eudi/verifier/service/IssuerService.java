package com.eudi.verifier.service;

import com.eudi.verifier.dto.response.CredentialResponse;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Emisor propio de credenciales de edad. Firma SD-JWT reales con RSA y expone su JWKS,
 * de modo que el verificador valida criptográficamente la credencial (sin tokens mock).
 */
@Service
public class IssuerService {

    private final SdJwtVerifierService sdJwtVerifierService;
    private final String issuerId;
    private final long credentialTtlSeconds;

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    private RSAKey publicJwk;
    private String keyId;

    public IssuerService(SdJwtVerifierService sdJwtVerifierService,
                         @Value("${app.issuer.id:https://issuer.eudi-demo.local}") String issuerId,
                         @Value("${app.issuer.credential-ttl-seconds:600}") long credentialTtlSeconds) {
        this.sdJwtVerifierService = sdJwtVerifierService;
        this.issuerId = issuerId;
        this.credentialTtlSeconds = credentialTtlSeconds;
    }

    @PostConstruct
    void initializeKeys() throws Exception {
        KeyPairGenerator keyGenerator = KeyPairGenerator.getInstance("RSA");
        keyGenerator.initialize(2048);
        KeyPair keyPair = keyGenerator.generateKeyPair();

        this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
        this.publicKey = (RSAPublicKey) keyPair.getPublic();
        this.keyId = "issuer-key-1";
        this.publicJwk = new RSAKey.Builder(publicKey).keyID(keyId).build();

        // El verificador confía en nuestro emisor: registramos su clave pública en memoria.
        sdJwtVerifierService.registerTrustedKey(issuerId, publicKey);
    }

    /**
     * Emite una credencial SD-JWT firmada con el atributo age_over_15.
     */
    public CredentialResponse issueAgeCredential(boolean ageOver15) {
        try {
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(credentialTtlSeconds);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuerId)
                    .subject("user-" + UUID.randomUUID())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiresAt))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("age_over_15", ageOver15)
                    .build();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(keyId)
                    .type(JOSEObjectType.JWT)
                    .build();

            SignedJWT signedJWT = new SignedJWT(header, claims);
            signedJWT.sign(new RSASSASigner(privateKey));

            // Formato SD-JWT: Issuer-signed JWT + "~" (separador de disclosures)
            String sdJwt = signedJWT.serialize() + "~";

            return new CredentialResponse(sdJwt, "vc+sd-jwt", expiresAt);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo emitir la credencial de edad", e);
        }
    }

    public String getJwks() {
        return new JWKSet(publicJwk).toString();
    }

    public String getIssuerId() {
        return issuerId;
    }

    public String getKeyId() {
        return keyId;
    }
}
