package com.unisphere.backend.social.posting.entity;

import com.unisphere.backend.social.posting.enums.MediaType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "post_media")
@Getter
@Setter
@NoArgsConstructor
public class PostMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    /** Bare object key (e.g. posts/9/uuid.jpeg) — never a resolved URL; see changeset 012. */
    @Column(name = "media_key", nullable = false, length = 500)
    private String mediaKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 5)
    private MediaType mediaType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
