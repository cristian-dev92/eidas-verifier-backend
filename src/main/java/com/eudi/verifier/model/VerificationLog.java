package com.eudi.verifier.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "verification_logs")
public class VerificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String nonce;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus status;

    @Column(name = "is_age_verified", nullable = false)
    private boolean isAgeVerified;

    @Column(name = "client_id")
    private String clientId; // ID de la app o empresa cliente que consulta nuestra API

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "error_message")
    private String errorMessage;

    public VerificationLog(String nonce, VerificationStatus status, boolean isAgeVerified, String clientId, String errorMessage) {
        this.nonce = nonce;
        this.status = status;
        this.isAgeVerified = isAgeVerified;
        this.clientId = clientId;
        this.errorMessage = errorMessage;
        this.createdAt = Instant.now();
    }
}