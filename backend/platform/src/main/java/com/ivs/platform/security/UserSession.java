package com.ivs.platform.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_session", schema = "ivs")
public class UserSession {

    @Id
    private UUID id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserSession() {
    }

    public static UserSession create(String tokenHash, UUID userId, Instant expiresAt) {
        UserSession session = new UserSession();
        session.id = UUID.randomUUID();
        session.tokenHash = tokenHash;
        session.userId = userId;
        session.expiresAt = expiresAt;
        session.createdAt = Instant.now();
        session.lastUsedAt = session.createdAt;
        return session;
    }

    public void touch(Instant now) { this.lastUsedAt = now; }
    public void revoke(Instant now) { this.revokedAt = now; }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = now;
        if (lastUsedAt == null) lastUsedAt = now;
    }

    public UUID getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public UUID getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
}
