package com.eudi.verifier.repository;

import com.eudi.verifier.model.VerificationLog;
import com.eudi.verifier.model.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationLogRepository extends JpaRepository<VerificationLog, UUID> {

    // Último registro asociado a un nonce (el nonce se usa como identificador del challenge)
    Optional<VerificationLog> findFirstByNonceOrderByCreatedAtDesc(String nonce);

    // Modo útil para métricas: buscar logs por cliente y estado
    List<VerificationLog> findByClientIdAndStatus(String clientId, VerificationStatus status);

    // Conteo para facturación o cuotas por cliente
    long countByClientIdAndStatus(String clientId, VerificationStatus status);
}
