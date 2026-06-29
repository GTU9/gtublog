package com.gtublog.automation;

import com.gtublog.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "automation_topic")
public class AutomationTopic extends BaseEntity {

    @Column(name = "slug", nullable = false, length = 120)
    private String slug;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "prompt_template_version", nullable = false, length = 64)
    private String promptTemplateVersion;

    @Column(name = "publication_enabled", nullable = false)
    private Boolean publicationEnabled;

    protected AutomationTopic() {
    }
}
