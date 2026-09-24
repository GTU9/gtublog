package com.gtublog.automation;

import com.gtublog.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "automation_origin_group")
public class AutomationOriginGroup extends BaseEntity {
    @Column(name = "topic_id", nullable = false)
    private Long topicId;
    @Column(name = "name", nullable = false, length = 120)
    private String name;
    @Column(name = "rationale", nullable = false, length = 1000)
    private String rationale;

    protected AutomationOriginGroup() {}

    private AutomationOriginGroup(Long topicId, String name, String rationale) {
        this.topicId = topicId;
        this.name = name;
        this.rationale = rationale;
    }

    public static AutomationOriginGroup create(Long topicId, String name, String rationale) {
        return new AutomationOriginGroup(topicId, name, rationale);
    }

    public Long getTopicId() { return topicId; }
    public String getName() { return name; }
    public String getRationale() { return rationale; }
}
