package com.unisphere.backend.campus.news.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Free-text topic tag. Note this is unlike post_tags, which tags *users* in a post.
 */
@Entity
@Table(name = "news_tags", uniqueConstraints = @UniqueConstraint(columnNames = {"article_id", "tag"}))
@Getter
@Setter
@NoArgsConstructor
public class NewsTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private NewsArticle article;

    @Column(nullable = false, length = 100)
    private String tag;
}
