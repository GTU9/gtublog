package com.gtublog.auth;

import com.gtublog.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_token_family")
public class RefreshTokenFamily extends BaseEntity {

    @Column(name = "family_key", nullable = false, length = 36)
    private String familyKey;

    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RefreshTokenFamilyStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "csrf_token_hash", nullable = false, length = 64)
    private String csrfTokenHash;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_reason", length = 255)
    private String revokedReason;

    protected RefreshTokenFamily() {
    }

    private RefreshTokenFamily(
            String familyKey,
            Long adminUserId,
            RefreshTokenFamilyStatus status,
            LocalDateTime expiresAt,
            String csrfTokenHash) {
        this.familyKey = familyKey;
        this.adminUserId = adminUserId;
        this.status = status;
        this.expiresAt = expiresAt;
        this.csrfTokenHash = csrfTokenHash;
    }

    public static RefreshTokenFamily create(
            String familyKey,
            Long adminUserId,
            LocalDateTime expiresAt,
            String csrfTokenHash) {
        return new RefreshTokenFamily(
                familyKey,
                adminUserId,
                RefreshTokenFamilyStatus.ACTIVE,
                expiresAt,
                csrfTokenHash);
    }

    public String getFamilyKey() {
        return familyKey;
    }

    public Long getAdminUserId() {
        return adminUserId;
    }

    public RefreshTokenFamilyStatus getStatus() {
        return status;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public String getCsrfTokenHash() {
        return csrfTokenHash;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public String getRevokedReason() {
        return revokedReason;
    }

    public boolean isActiveAt(LocalDateTime now) {
        return status == RefreshTokenFamilyStatus.ACTIVE
                && revokedAt == null
                && expiresAt.isAfter(now);
    }

    public void rotateCsrfToken(String csrfTokenHash) {
        this.csrfTokenHash = csrfTokenHash;
    }

    public void revoke(LocalDateTime revokedAt, String reason) {
        this.status = RefreshTokenFamilyStatus.REVOKED;
        this.revokedAt = revokedAt;
        this.revokedReason = reason;
    }
}
