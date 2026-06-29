package com.gtublog.auth;

import com.gtublog.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_token")
public class RefreshToken extends BaseEntity {

    @Column(name = "token_key", nullable = false, length = 36)
    private String tokenKey;

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "predecessor_id")
    private Long predecessorId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "rotated_at")
    private LocalDateTime rotatedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "reuse_detected_at")
    private LocalDateTime reuseDetectedAt;

    protected RefreshToken() {
    }

    private RefreshToken(
            String tokenKey,
            Long familyId,
            Long predecessorId,
            String tokenHash,
            LocalDateTime expiresAt) {
        this.tokenKey = tokenKey;
        this.familyId = familyId;
        this.predecessorId = predecessorId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken issue(
            String tokenKey,
            Long familyId,
            Long predecessorId,
            String tokenHash,
            LocalDateTime expiresAt) {
        return new RefreshToken(tokenKey, familyId, predecessorId, tokenHash, expiresAt);
    }

    public String getTokenKey() {
        return tokenKey;
    }

    public Long getFamilyId() {
        return familyId;
    }

    public Long getPredecessorId() {
        return predecessorId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getRotatedAt() {
        return rotatedAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public LocalDateTime getReuseDetectedAt() {
        return reuseDetectedAt;
    }

    public boolean isUsableAt(LocalDateTime now) {
        return rotatedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    public void markRotated(LocalDateTime rotatedAt) {
        this.rotatedAt = rotatedAt;
    }

    public void markRevoked(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public void markReuseDetected(LocalDateTime reuseDetectedAt) {
        this.reuseDetectedAt = reuseDetectedAt;
    }
}
