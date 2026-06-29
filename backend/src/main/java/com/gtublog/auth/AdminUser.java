package com.gtublog.auth;

import com.gtublog.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_user")
public class AdminUser extends BaseEntity {

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AdminUserStatus status;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    protected AdminUser() {
    }

    private AdminUser(String username, String passwordHash, String displayName, AdminUserStatus status) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.status = status;
    }

    public static AdminUser bootstrap(String username, String passwordHash, String displayName) {
        return new AdminUser(username, passwordHash, displayName, AdminUserStatus.ACTIVE);
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AdminUserStatus getStatus() {
        return status;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public boolean isActive() {
        return status == AdminUserStatus.ACTIVE;
    }

    public void markLoggedIn(LocalDateTime loggedInAt) {
        this.lastLoginAt = loggedInAt;
    }
}
