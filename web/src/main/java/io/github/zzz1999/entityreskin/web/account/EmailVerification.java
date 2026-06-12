package io.github.zzz1999.entityreskin.web.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A pending email verification code (stored hashed, with an expiry). */
@Entity
@Table(name = "email_verifications")
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String codeHash;

    /** The account password (hashed) submitted at registration; applied to the user on verify. */
    @Column
    private String pendingPasswordHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected EmailVerification() {
    }

    public EmailVerification(String email, String codeHash, String pendingPasswordHash, Instant expiresAt) {
        this.email = email;
        this.codeHash = codeHash;
        this.pendingPasswordHash = pendingPasswordHash;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public String getPendingPasswordHash() {
        return pendingPasswordHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
