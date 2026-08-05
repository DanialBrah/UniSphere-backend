package com.unisphere.backend.campus.news.entity;

import com.unisphere.backend.campus.news.enums.NewsMediaType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "news_media")
@Getter
@Setter
@NoArgsConstructor
public class NewsMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private NewsArticle article;

    /** Bare object key (e.g. news/9/uuid.jpeg) — never a resolved URL; see changeset 012. */
    @Column(name = "media_key", nullable = false, length = 500)
    private String mediaKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 5)
    private NewsMediaType mediaType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
