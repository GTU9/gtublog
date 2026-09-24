package com.gtublog.automation;

public final class AutomationHoldReason {

    public static final String NO_ENABLED_SOURCES = "No enabled automation sources are configured for this topic.";
    public static final String AUTOMATIC_PUBLICATION_DISABLED = "Automatic publication is disabled for this topic.";
    public static final String SOURCE_BLOCKED = "One or more required source snapshots are inaccessible or blocked.";
    public static final String INSUFFICIENT_ORIGINS = "Material claims require corroboration across at least two independent origin hosts.";
    public static final String DUPLICATE_PUBLICATION = "A matching canonical source or content fingerprint has already been published.";
    public static final String ADMINISTRATOR_CANCELLED = "Cancelled by the administrator.";
    public static final String TAXONOMY_CATALOG_EMPTY = "No category or tag candidates are configured for automatic publication.";
    public static final String TAXONOMY_CATALOG_TOO_LARGE = "Taxonomy catalog exceeds 100 categories or 200 tags.";
    public static final String TAXONOMY_CATALOG_INVALID = "Taxonomy catalog contains an invalid category or tag.";
    public static final String TAXONOMY_SELECTION_MISSING = "Generated draft has no valid taxonomy selection.";
    public static final String TAXONOMY_DUPLICATE_TAG = "Generated draft selected the same tag more than once.";
    public static final String TAXONOMY_SELECTION_OUTSIDE_CATALOG = "Generated taxonomy selection is outside the job catalog.";
    public static final String TAXONOMY_CATEGORY_CHANGED = "Selected category was deleted or changed since the job was created.";
    public static final String TAXONOMY_TAG_CHANGED = "A selected tag was deleted or changed since the job was created.";

    private AutomationHoldReason() {
    }
}
