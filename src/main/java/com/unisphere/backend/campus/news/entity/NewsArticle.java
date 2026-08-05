package com.unisphere.backend.campus.news.entity;

import com.unisphere.backend.campus.news.enums.NewsCategory;
import com.unisphere.backend.campus.news.enums.NewsStatus;
import com.unisphere.backend.campus.news.enums.NewsVisibility;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "news_articles")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class NewsArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    /**
     * Always derived server-side from the author's own affiliation, never from the request —
     * UNIVERSITY visibility's guarantee depends on it. See NewsAccessService.
     */
    @Column(name = "university_id")
    private Long universityId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 500)
    private String summary;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    /** Bare object key (e.g. news/9/uuid.jpeg) — never a resolved URL; see changeset 012. */
    @Column(name = "cover_image_key", length = 500)
    private String coverImageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private NewsCategory category = NewsCategory.GENERAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 9)
    private NewsStatus status = NewsStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NewsVisibility visibility = NewsVisibility.PUBLIC;

    @Column(name = "is_featured", nullable = false)
    private boolean featured = false;

    @Column(name = "views_count", nullable = false)
    private int viewsCount = 0;

    @Column(name = "likes_count", nullable = false)
    private int likesCount = 0;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /** Non-null on a DRAFT means "queued" — NewsPublishScheduler sweeps these once due. */
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // @BatchSize rather than a join fetch: these are paginated queries, and fetching a collection
    // alongside firstResult/maxResults forces Hibernate to paginate in memory. Batching instead
    // loads the collections for up to 50 articles per extra query, which keeps pagination in SQL.
    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @BatchSize(size = 50)
    private List<NewsMedia> media = new ArrayList<>();

    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<NewsTag> tags = new ArrayList<>();
}
