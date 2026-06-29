package com.gtublog.post;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PostRepository extends JpaRepository<Post, Long> {

    Optional<Post> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("""
            select post from Post post
            where post.deletedAt is null
            order by post.createdAt desc
            """)
    Page<Post> findAllActive(Pageable pageable);

    @Query("""
            select post from Post post
            where post.status = com.gtublog.post.PostStatus.PUBLISHED
              and post.deletedAt is null
            order by post.firstPublishedAt desc, post.id desc
            """)
    Page<Post> findPublished(Pageable pageable);

    @Query("""
            select post from Post post
            where post.slug = :slug
              and post.status = com.gtublog.post.PostStatus.PUBLISHED
              and post.deletedAt is null
            """)
    Optional<Post> findPublishedBySlug(String slug);
}
