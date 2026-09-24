package com.gtublog.automation;

import com.gtublog.audit.AuditActorType;
import com.gtublog.audit.AuditService;
import com.gtublog.audit.AuditTargetType;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OriginPairAdminService {

    private final AutomationTopicRepository topics;
    private final AutomationOriginGroupRepository groups;
    private final AutomationOriginPairApprovalRepository pairs;
    private final AuditService audit;

    public OriginPairAdminService(
            AutomationTopicRepository topics,
            AutomationOriginGroupRepository groups,
            AutomationOriginPairApprovalRepository pairs,
            AuditService audit) {
        this.topics = topics;
        this.groups = groups;
        this.pairs = pairs;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<OriginPairResponse> pairs(Long topicId) {
        requireTopic(topicId);
        return pairs.findAllByTopicIdOrderByIdDesc(topicId).stream()
                .map(this::response)
                .toList();
    }

    @Transactional
    public OriginPairResponse approve(Long topicId, OriginPairRequest request) {
        topics.findByIdForUpdate(topicId)
                .orElseThrow(() -> new NoSuchElementException("Automation topic not found."));
        var first = groups.findById(request.firstGroupId())
                .orElseThrow(() -> new NoSuchElementException("Origin group not found."));
        var second = groups.findById(request.secondGroupId())
                .orElseThrow(() -> new NoSuchElementException("Origin group not found."));
        if (first.getId().equals(second.getId())) {
            throw new IllegalArgumentException("Origin pair approval requires two different groups.");
        }
        if (!topicId.equals(first.getTopicId()) || !topicId.equals(second.getTopicId())) {
            throw new IllegalArgumentException("Origin pair groups must belong to the requested topic.");
        }

        var groupLowId = Math.min(first.getId(), second.getId());
        var groupHighId = Math.max(first.getId(), second.getId());
        if (pairs.findByTopicIdAndGroupLowIdAndGroupHighIdAndActiveTrue(topicId, groupLowId, groupHighId).isPresent()) {
            throw new AutomationConfigurationConflictException("Origin pair already has an active approval for this topic.");
        }

        var pair = pairs.saveAndFlush(AutomationOriginPairApproval.approve(
                topicId,
                groupLowId,
                groupHighId,
                request.rationale().trim()));
        audit.record(
                AuditActorType.ADMIN,
                "1",
                AuditTargetType.AUTOMATION,
                pair.getId().toString(),
                "ORIGIN_PAIR_APPROVAL_GRANTED",
                Map.of(
                        "topicId", topicId,
                        "groupLowId", groupLowId,
                        "groupHighId", groupHighId,
                        "revision", pair.getRevision(),
                        "rationale", pair.getRationale()));
        return response(pair);
    }

    @Transactional
    public OriginPairResponse revoke(Long pairId, OriginPairRevokeRequest request) {
        var pair = pairs.findById(pairId)
                .orElseThrow(() -> new NoSuchElementException("Origin pair approval not found."));
        topics.findByIdForUpdate(pair.getTopicId())
                .orElseThrow(() -> new NoSuchElementException("Automation topic not found."));
        if (pairs.revokeIfCurrent(pairId, request.revision(), request.rationale().trim()) != 1) {
            throw new AutomationConfigurationConflictException("Origin pair approval revision is stale or already revoked.");
        }
        audit.record(
                AuditActorType.ADMIN,
                "1",
                AuditTargetType.AUTOMATION,
                pairId.toString(),
                "ORIGIN_PAIR_APPROVAL_REVOKED",
                Map.of(
                        "topicId", pair.getTopicId(),
                        "groupLowId", pair.getGroupLowId(),
                        "groupHighId", pair.getGroupHighId(),
                        "revision", request.revision() + 1,
                        "rationale", request.rationale().trim()));
        return response(pairs.findById(pairId).orElseThrow());
    }

    private void requireTopic(Long topicId) {
        if (!topics.existsById(topicId)) {
            throw new NoSuchElementException("Automation topic not found.");
        }
    }

    private OriginPairResponse response(AutomationOriginPairApproval pair) {
        var low = groups.findById(pair.getGroupLowId()).orElseThrow();
        var high = groups.findById(pair.getGroupHighId()).orElseThrow();
        return new OriginPairResponse(
                pair.getId(),
                pair.getTopicId(),
                pair.getGroupLowId(),
                pair.getGroupHighId(),
                low.getName(),
                high.getName(),
                pair.getRationale(),
                pair.getRevocationRationale(),
                pair.isActive(),
                pair.getRevision(),
                pair.getApprovedAt(),
                pair.getRevokedAt(),
                pair.getCreatedAt(),
                pair.getUpdatedAt());
    }
}
