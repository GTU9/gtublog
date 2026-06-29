package com.gtublog.audit;

import com.gtublog.post.PostPageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit")
public class AdminAuditController {

    private final AuditEntryRepository auditEntryRepository;

    public AdminAuditController(AuditEntryRepository auditEntryRepository) {
        this.auditEntryRepository = auditEntryRepository;
    }

    @GetMapping
    public PostPageResponse<AuditEntryResponse> entries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var boundedSize = Math.max(1, Math.min(size <= 0 ? 20 : size, 100));
        var result = auditEntryRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, boundedSize));
        return new PostPageResponse<>(
                result.getContent().stream()
                        .map(entry -> new AuditEntryResponse(
                                entry.getId(),
                                entry.getActorType(),
                                entry.getActorId(),
                                entry.getTargetType(),
                                entry.getTargetId(),
                                entry.getActionType(),
                                entry.getDetailJson(),
                                entry.getCreatedAt()))
                        .toList(),
                page,
                boundedSize,
                result.getTotalElements(),
                result.getTotalPages());
    }
}
