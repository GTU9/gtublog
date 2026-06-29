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
}
