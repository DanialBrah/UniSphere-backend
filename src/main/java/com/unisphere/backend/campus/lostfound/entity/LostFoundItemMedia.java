package com.unisphere.backend.campus.lostfound.entity;

import com.unisphere.backend.campus.lostfound.enums.LostFoundMediaType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Gallery photo/video attached to a report — the NewsMedia shape.
 *
 * <p>No soft delete and no auditing: rows live and die with their parent through
 * {@code cascade = ALL, orphanRemoval = true}.
 *
 * <p>Hidden from unprivileged viewers of a FOUND report: more photos make a fabricated claim more
 * convincing. {@code LostFoundItem.primaryImageKey} stays visible so the item is still
 * recognisable at all.
 */
@Entity
@Table(name = "lost_found_item_media")
@Getter
@Setter
@NoArgsConstructor
public class LostFoundItemMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private LostFoundItem item;

    /** Bare object key (e.g. lost-found/9/uuid.jpeg) — never a resolved URL; see changeset 012. */
    @Column(name = "media_key", nullable = false, length = 500)
    private String mediaKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 5)
    private LostFoundMediaType mediaType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
