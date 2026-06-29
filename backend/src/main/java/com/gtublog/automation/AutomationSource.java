package com.gtublog.automation;

import com.gtublog.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "automation_source")
public class AutomationSource extends BaseEntity {

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private AutomationSourceType sourceType;

    @Column(name = "source_url", nullable = false, length = 512)
    private String sourceUrl;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    protected AutomationSource() {
    }

    private AutomationSource(Long topicId, AutomationSourceType sourceType, String sourceUrl, boolean enabled) {
        this.topicId = topicId;
        this.sourceType = sourceType;
        this.sourceUrl = sourceUrl;
        this.enabled = enabled;
    }

    public static AutomationSource create(Long topicId, AutomationSourceType sourceType, String sourceUrl, boolean enabled) {
        return new AutomationSource(topicId, sourceType, sourceUrl, enabled);
    }

    public Long getId() {
        return super.getId();
    }

    public Long getTopicId() {
        return topicId;
    }

    public AutomationSourceType getSourceType() {
        return sourceType;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public void update(AutomationSourceType sourceType, String sourceUrl, boolean enabled) {
        this.sourceType = sourceType;
        this.sourceUrl = sourceUrl;
        this.enabled = enabled;
    }
}
