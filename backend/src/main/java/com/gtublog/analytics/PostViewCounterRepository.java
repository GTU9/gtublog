package com.gtublog.analytics;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostViewCounterRepository extends JpaRepository<PostViewCounter, Long> {

    Optional<PostViewCounter> findByPostId(Long postId);
}
