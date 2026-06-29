package com.gtublog.audit;

import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuditService {

    private final AuditEntryRepository auditEntryRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditEntryRepository auditEntryRepository, ObjectMapper objectMapper) {
        this.auditEntryRepository = auditEntryRepository;
        this.objectMapper = objectMapper;
    }

    public void record(
            AuditActorType actorType,
            String actorId,
            AuditTargetType targetType,
            String targetId,
            String actionType,
            Map<String, Object> detail) {
        auditEntryRepository.save(AuditEntry.create(
                actorType,
                actorId,
                targetType,
                targetId,
                actionType,
                toJson(detail)));
    }

    private String toJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        }
        catch (Exception exception) {
            throw new IllegalArgumentException("Could not serialize audit detail.", exception);
        }
    }
}
