package com.gtublog.automation;

public final class AutomationHoldReason {

    public static final String NO_ENABLED_SOURCES = "No enabled automation sources are configured for this topic.";
    public static final String AUTOMATIC_PUBLICATION_DISABLED = "Automatic publication is disabled for this topic.";
    public static final String SOURCE_BLOCKED = "One or more required source snapshots are inaccessible or blocked.";
    public static final String INSUFFICIENT_ORIGINS = "Material claims require corroboration across at least two independent origin hosts.";
    public static final String DUPLICATE_PUBLICATION = "A matching canonical source or content fingerprint has already been published.";
    public static final String ADMINISTRATOR_CANCELLED = "Cancelled by the administrator.";

    private AutomationHoldReason() {
    }
}
