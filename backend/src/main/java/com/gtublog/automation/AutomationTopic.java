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

    private AutomationTopic(String slug, String name, String promptTemplateVersion, boolean publicationEnabled) {
        this.slug = slug;
        this.name = name;
        this.promptTemplateVersion = promptTemplateVersion;
        this.publicationEnabled = publicationEnabled;
    }

    public static AutomationTopic create(String slug, String name, String promptTemplateVersion, boolean publicationEnabled) {
        return new AutomationTopic(slug, name, promptTemplateVersion, publicationEnabled);
    }

    public Long getId() {
        return super.getId();
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getPromptTemplateVersion() {
        return promptTemplateVersion;
    }

    public boolean isPublicationEnabled() {
        return Boolean.TRUE.equals(publicationEnabled);
    }

    public void update(String slug, String name, String promptTemplateVersion, boolean publicationEnabled) {
        this.slug = slug;
        this.name = name;
        this.promptTemplateVersion = promptTemplateVersion;
        this.publicationEnabled = publicationEnabled;
    }
}
