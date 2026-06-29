package com.gtublog.post;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRevisionRepository extends JpaRepository<PostRevision, Long> {

    List<PostRevision> findByPostIdOrderByRevisionNumberDesc(Long postId);

    Optional<PostRevision> findByPostIdAndRevisionNumber(Long postId, Integer revisionNumber);
}
