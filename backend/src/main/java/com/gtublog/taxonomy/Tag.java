package com.gtublog.taxonomy;

import com.gtublog.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "tag")
public class Tag extends BaseEntity {

    @Column(name = "slug", nullable = false, length = 120)
    private String slug;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    protected Tag() {
    }

    private Tag(String slug, String name, String description) {
        this.slug = slug;
        this.name = name;
        this.description = description;
    }

    public static Tag create(String slug, String name, String description) {
        return new Tag(slug, name, description);
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

    public String getDescription() {
        return description;
    }

    public void update(String slug, String name, String description) {
        this.slug = slug;
        this.name = name;
        this.description = description;
    }
}
