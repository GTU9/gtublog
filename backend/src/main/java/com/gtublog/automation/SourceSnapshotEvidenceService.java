package com.gtublog.automation;

import com.gtublog.source.SourcePolicyResult;
import com.gtublog.source.SourceSnapshot;
import com.gtublog.source.SourceSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SourceSnapshotEvidenceService {
    private final AutomationSourceRepository sources;
    private final AutomationOriginApprovalRepository approvals;
    private final SourceSnapshotRepository snapshots;

    public SourceSnapshotEvidenceService(AutomationSourceRepository sources,
                                         AutomationOriginApprovalRepository approvals,
                                         SourceSnapshotRepository snapshots) {
        this.sources = sources;
        this.approvals = approvals;
        this.snapshots = snapshots;
    }

    @Transactional
    public SourceSnapshot save(SourceSnapshot snapshot, AutomationSource collectedSource) {
        if (snapshot.getPolicyResult() == SourcePolicyResult.ALLOWED && collectedSource.getId() != null) {
            // Serializes policy edits, revocation, and evidence capture without holding a
            // transaction during the network request.
            var current = sources.findLockedById(collectedSource.getId()).orElse(null);
            if (current != null && current.getSourceUrl().equals(collectedSource.getSourceUrl())
                    && current.getSourceType() == collectedSource.getSourceType()) {
                approvals.findBySourceIdAndOriginHostAndActiveTrue(current.getId(), snapshot.getOriginHost())
                        .filter(approval -> approval.matches(current, snapshot.getOriginHost()))
                        .ifPresent(approval -> snapshot.captureOriginApproval(
                                approval.getId(), approval.getRevision(), approval.getGroupId()));
            }
        }
        return snapshots.save(snapshot);
    }
}
