package com.gtublog.audit;

import com.gtublog.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_entry")
public class AuditEntry extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 32)
    private AuditActorType actorType;

    @Column(name = "actor_id", nullable = false, length = 120)
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    private AuditTargetType targetType;

    @Column(name = "target_id", nullable = false, length = 120)
    private String targetId;

    @Column(name = "action_type", nullable = false, length = 64)
    private String actionType;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "detail_json", columnDefinition = "longtext")
    private String detailJson;

    protected AuditEntry() {
    }
}
