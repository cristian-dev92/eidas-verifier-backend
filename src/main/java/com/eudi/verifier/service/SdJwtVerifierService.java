package com.eudi.verifier.service;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import lombok.Getter;
import lombok.Setter;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
@Service
public class SdJwtVerifierService {

    // Caché local en memoria para los JWKS de los emisores oficiales (TTL recomendado en producción)
    private final Map<String, JWKSet> issuerJwksCache = new ConcurrentHashMap<>();

    // Almacén para claves públicas estáticas (muy útil para tests unitarios y entornos sin salida a internet)
    private final Map<String, RSAPublicKey> staticTrustedKeys = new ConcurrentHashMap<>();

    private final Environment environment;

    public SdJwtVerifierService(Environment environment) {
        this.environment = environment;
    }

    /**
     * Permite registrar una clave pública de confianza de forma local (ej. para tests o emisores locales).
     */
    public void registerTrustedKey(String issuer, RSAPublicKey publicKey) {
        staticTrustedKeys.put(issuer, publicKey);
    }

    /**
     * Parsea y valida el token SD-JWT / VP enviado por el EUDI Wallet.
     * Devuelve un resultado detallado para distinguir firma inválida de edad no cumplida.
     */
    public SdJwtVerificationResult verifySdJwt(String vpToken) {
        if (vpToken == null || vpToken.isBlank()) {
            return SdJwtVerificationResult.INVALID_PROOF;
        }

        // --- MODO DE PRUEBAS (solo con perfil "dev" y el flag local activado) ---
        // En producción el flag no existe (el archivo dev está ignorado por git),
        // por lo que se exige siempre la validación criptográfica real.
        if (isDevMockAllowed() && isMockToken(vpToken)) {
            return verifyDevMockProof(vpToken)
                    ? SdJwtVerificationResult.VALID
                    : SdJwtVerificationResult.INVALID_PROOF;
        }
        // ------------------------------------------------------------------------

        try {
            // 1. El formato SD-JWT suele venir separado por tildes (~).
            // La primera parte antes del primer '~' es el JWT firmado por el emisor (Issuer-signed JWT).
            String issuerJwtString = vpToken.contains("~") ? vpToken.split("~")[0] : vpToken;
            // Parsear el JWT firmado usando Nimbus
            SignedJWT signedJWT = SignedJWT.parse(issuerJwtString);

            // 2. Obtener el emisor (iss) y el Key ID (kid)
            String issuer = signedJWT.getJWTClaimsSet().getIssuer();
            String keyId = signedJWT.getHeader().getKeyID();

            // 3. Obtener la clave pública oficial del emisor desde su registro/JWKS
            RSAPublicKey publicKey = getPublicKeyFromTrustRegistry(issuer, keyId);
            if (publicKey == null) {
                return SdJwtVerificationResult.INVALID_PROOF; // Emisor no confiable o clave no encontrada
            }

            // 4. Verificación Criptográfica de la Firma (JWS)
            JWSVerifier verifier = new RSASSAVerifier(publicKey);
            if (!signedJWT.verify(verifier)) {
                return SdJwtVerificationResult.INVALID_PROOF; // La firma digital es falsa o el documento fue alterado
            }

            // Extraer las declaraciones (Claims) del payload
            Map<String, Object> claims = signedJWT.getPayload().toJSONObject();
            Object ageOver15 = claims.get("age_over_15");
            if (ageOver15 == null) {
                return SdJwtVerificationResult.INVALID_PROOF;
            }
            return Boolean.TRUE.equals(ageOver15)
                    ? SdJwtVerificationResult.VALID
                    : SdJwtVerificationResult.AGE_NOT_MET;

        } catch (Exception e) {
            return SdJwtVerificationResult.INVALID_PROOF;
        }
    }

    /**
     * Compatibilidad: true solo si la credencial es válida y cumple la edad.
     */
    public boolean verifySdJwtProof(String vpToken) {
        return verifySdJwt(vpToken) == SdJwtVerificationResult.VALID;
    }

    /**
     * Bypass de desarrollo: SOLO se invoca cuando el perfil "dev" está activo.
     */
    public boolean verifyDevMockProof(String vpToken) {
        if (vpToken.contains("invalid_mock_vp_token_signature")) {
            return false;
        }
        return vpToken.startsWith("eyJhbGciOiJFUzI1NiJ9") || vpToken.contains("mock");
    }

    private boolean isMockToken(String vpToken) {
        return vpToken.contains("invalid_mock_vp_token_signature")
                || vpToken.startsWith("eyJhbGciOiJFUzI1NiJ9")
                || vpToken.contains("mock");
    }

    private boolean isDevMockAllowed() {
        return environment.matchesProfiles("dev")
                && environment.getProperty("app.verifier.allow-dev-mock", Boolean.class, false);
    }

    /**
     * Simula la obtención de la clave pública desde el Trust Registry o JWKS del emisor oficial.
     */
    private RSAPublicKey getPublicKeyFromTrustRegistry(String issuer, String keyId) {
        // 1. Si existe una clave estática registrada para este emisor, se usa directamente (ideal para tests)
        if (staticTrustedKeys.containsKey(issuer)) {
            return staticTrustedKeys.get(issuer);
        }

        try {
            // En producción, aquí consultarías la URL del JWKS del emisor:
            // Ej: https://issuer.europa.eu/.well-known/jwks.json o la Trust List oficial de la UE.
            JWKSet jwkSet = issuerJwksCache.computeIfAbsent(issuer, iss -> {
                try {
                    // Descarga real de las claves públicas del emisor
                    return JWKSet.load(new URL(iss + "/.well-known/jwks.json"));
                } catch (Exception e) {
                    return null;
                }
            });

            if (jwkSet == null) return null;

            JWK jwk = (keyId != null) ? jwkSet.getKeyByKeyId(keyId) : jwkSet.getKeys().get(0);
            if (jwk instanceof RSAKey rsaKey) {
                return rsaKey.toRSAPublicKey();
            }

        } catch (Exception e) {
            // Error al conectar con el registro o parsear la clave
        }
        return null;
    }
}