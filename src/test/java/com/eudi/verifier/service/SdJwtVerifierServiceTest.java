package com.eudi.verifier.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class SdJwtVerifierServiceTest {

    private SdJwtVerifierService sdJwtVerifierService;
    private RSAPrivateKey privateKey;
    private static final String TEST_ISSUER = "https://trusted-issuer.europa.eu";

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        sdJwtVerifierService = new SdJwtVerifierService(environment);

        // Generamos un par de claves RSA simuladas para firmar el JWT de prueba
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        // Registramos la clave pública en el servicio para que pueda validar sin ir a internet
        sdJwtVerifierService.registerTrustedKey(TEST_ISSUER, publicKey);
    }

    @Test
    @DisplayName("Debe validar con éxito un SD-JWT que contiene age_over_15 en true")
    void testVerifyValidAgeProof() throws Exception {
        // 1. Crear el payload con la regla de negocio requerida por la UE
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("user-sub-123")
                .issuer("https://trusted-issuer.europa.eu")
                .expirationTime(new Date(System.currentTimeMillis() + 3600 * 1000))
                .claim("age_over_15", true)
                .build();

        String signedJwtString = createSignedJwtString(claimsSet);

        // Simulamos el formato SD-JWT con separador de disclosures (Issuer JWT + '~' + disclosure)
        String mockSdJwtToken = signedJwtString + "~WyJpc19hZHVsdCIsIDE4XQ";

        // 2. Ejecutar la validación en el servicio
        boolean isValid = sdJwtVerifierService.verifySdJwtProof(mockSdJwtToken);

        // 3. Aserciones
        assertTrue(isValid, "El token debería ser verificado como válido para mayores de 15 años.");
    }

    @Test
    @DisplayName("Debe rechazar un token donde age_over_15 sea false")
    void testVerifyUnderageProof() throws Exception {
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("user-sub-456")
                .issuer("https://trusted-issuer.europa.eu")
                .claim("age_over_15", false)
                .build();

        String signedJwtString = createSignedJwtString(claimsSet);

        boolean isValid = sdJwtVerifierService.verifySdJwtProof(signedJwtString);

        assertFalse(isValid, "El token debe ser rechazado si el usuario es menor de 15.");
    }

    @Test
    @DisplayName("Debe retornar falso si el token es nulo o está vacío")
    void testVerifyInvalidToken() {
        assertFalse(sdJwtVerifierService.verifySdJwtProof(null));
        assertFalse(sdJwtVerifierService.verifySdJwtProof(""));
        assertFalse(sdJwtVerifierService.verifySdJwtProof("token.invalido.malformado"));
    }

    // Modo auxiliar para firmar un JWT con Nimbus
    private String createSignedJwtString(JWTClaimsSet claimsSet) throws JOSEException {
        JWSSigner signer = new RSASSASigner(privateKey);
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
                claimsSet
        );
        signedJWT.sign(signer);
        return signedJWT.serialize();
    }
}
