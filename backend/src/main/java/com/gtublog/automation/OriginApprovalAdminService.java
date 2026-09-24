package com.gtublog.automation;

import com.gtublog.audit.AuditActorType;
import com.gtublog.audit.AuditService;
import com.gtublog.audit.AuditTargetType;
import java.net.IDN;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OriginApprovalAdminService {
    private final AutomationTopicRepository topics;
    private final AutomationSourceRepository sources;
    private final AutomationOriginGroupRepository groups;
    private final AutomationOriginApprovalRepository approvals;
    private final AuditService audit;

    public OriginApprovalAdminService(AutomationTopicRepository topics, AutomationSourceRepository sources,
                                      AutomationOriginGroupRepository groups,
                                      AutomationOriginApprovalRepository approvals, AuditService audit) {
        this.topics = topics;
        this.sources = sources;
        this.groups = groups;
        this.approvals = approvals;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<OriginGroupResponse> groups(Long topicId) {
        requireTopic(topicId);
        return groups.findAllByTopicIdOrderByIdAsc(topicId).stream().map(this::groupResponse).toList();
    }

    @Transactional
    public OriginGroupResponse createGroup(Long topicId, OriginGroupRequest request) {
        requireTopic(topicId);
        var name = request.name().trim();
        if (groups.existsByTopicIdAndName(topicId, name)) {
            throw new AutomationConfigurationConflictException("Origin group name already exists in this topic.");
        }
        var group = groups.saveAndFlush(AutomationOriginGroup.create(topicId, name, request.rationale().trim()));
        audit.record(AuditActorType.ADMIN, "1", AuditTargetType.AUTOMATION, group.getId().toString(),
                "ORIGIN_GROUP_CREATED", Map.of("topicId", topicId, "name", name));
        return groupResponse(group);
    }

    @Transactional(readOnly = true)
    public List<OriginApprovalResponse> approvals(Long sourceId) {
        requireSource(sourceId);
        return approvals.findAllBySourceIdOrderByIdDesc(sourceId).stream().map(this::approvalResponse).toList();
    }

    @Transactional
    public OriginApprovalResponse approve(Long sourceId, OriginApprovalRequest request) {
        var source = sources.findLockedById(sourceId)
                .orElseThrow(() -> new NoSuchElementException("Automation source not found."));
        var host = normalizedHost(request.originHost());
        var group = groups.findById(request.groupId()).orElseThrow(() -> new NoSuchElementException("Origin group not found."));
        if (!group.getTopicId().equals(source.getTopicId())) {
            throw new IllegalArgumentException("Origin group belongs to another topic.");
        }
        if (approvals.findBySourceIdAndOriginHostAndActiveTrue(sourceId, host).isPresent()) {
            throw new AutomationConfigurationConflictException("Origin host already has an active approval for this source.");
        }
        var approval = approvals.saveAndFlush(AutomationOriginApproval.approve(source, host, group.getId(), request.rationale().trim()));
        audit.record(AuditActorType.ADMIN, "1", AuditTargetType.AUTOMATION, approval.getId().toString(),
                "ORIGIN_APPROVAL_GRANTED", Map.of("sourceId", sourceId, "originHost", host,
                        "groupId", group.getId(), "revision", approval.getRevision(), "rationale", approval.getRationale()));
        return approvalResponse(approval);
    }

    @Transactional
    public OriginApprovalResponse revoke(Long approvalId, OriginApprovalRevokeRequest request) {
        var approval = approvals.findById(approvalId).orElseThrow(() -> new NoSuchElementException("Origin approval not found."));
        sources.findLockedById(approval.getSourceId()).orElseThrow(() -> new NoSuchElementException("Automation source not found."));
        if (approvals.revokeIfCurrent(approvalId, request.revision(), request.rationale().trim()) != 1) {
            throw new AutomationConfigurationConflictException("Origin approval revision is stale or already revoked.");
        }
        audit.record(AuditActorType.ADMIN, "1", AuditTargetType.AUTOMATION, approvalId.toString(),
                "ORIGIN_APPROVAL_REVOKED", Map.of("sourceId", approval.getSourceId(), "originHost", approval.getOriginHost(),
                        "revision", request.revision() + 1, "rationale", request.rationale().trim()));
        return approvalResponse(approvals.findById(approvalId).orElseThrow());
    }

    private String normalizedHost(String raw) {
        var value = raw.trim();
        if (value.isEmpty() || value.contains(":") || value.contains("/") || value.contains("*") || value.endsWith(".")) {
            throw new IllegalArgumentException("Enter one exact article origin host without scheme, port, path, or wildcard.");
        }
        try {
            var host = IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            if (host.length() > 255 || !host.contains(".") || !host.matches("[a-z0-9-]+(\\.[a-z0-9-]+)+")) {
                throw new IllegalArgumentException("Enter a valid article origin host.");
            }
            return host;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Enter a valid article origin host.", exception);
        }
    }

    private void requireTopic(Long id) {
        if (!topics.existsById(id)) throw new NoSuchElementException("Automation topic not found.");
    }
    private AutomationSource requireSource(Long id) {
        return sources.findById(id).orElseThrow(() -> new NoSuchElementException("Automation source not found."));
    }
    private OriginGroupResponse groupResponse(AutomationOriginGroup group) {
        return new OriginGroupResponse(group.getId(), group.getTopicId(), group.getName(), group.getRationale(),
                group.getCreatedAt(), group.getUpdatedAt());
    }
    private OriginApprovalResponse approvalResponse(AutomationOriginApproval approval) {
        var group = groups.findById(approval.getGroupId()).orElseThrow();
        return new OriginApprovalResponse(approval.getId(), approval.getSourceId(), approval.getOriginHost(),
                approval.getGroupId(), group.getName(), approval.getRationale(),
                approval.getRevocationRationale(), approval.isActive(),
                approval.getRevision(), approval.getApprovedAt(), approval.getRevokedAt(),
                approval.getCreatedAt(), approval.getUpdatedAt());
    }
}
