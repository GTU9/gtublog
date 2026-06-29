package com.gtublog.analytics;

import com.gtublog.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "post_view_counter")
public class PostViewCounter extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "view_count", nullable = false)
    private Long viewCount;

    @Column(name = "last_viewed_at")
    private LocalDateTime lastViewedAt;

    protected PostViewCounter() {
    }
}
