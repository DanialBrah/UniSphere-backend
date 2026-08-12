package com.unisphere.backend.campus.lostfound.entity;

import com.unisphere.backend.campus.lostfound.enums.LostFoundCategory;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemStatus;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A lost-item or found-item report.
 *
 * <p>Carries two independent locations. {@code incident*} is where the item was lost or found —
 * the pin the board renders on the map. {@code pickup*} is where it can be collected, which is a
 * different place (found by the fountain, held at the faculty office) and is the answer to "how do
 * I get it back". On a LOST report {@code pickupPlace} inverts naturally to "where to return it to
 * me". Both pairs are nullable: geolocation is optional, and a report typed on a phone must not be
 * blocked by a denied GPS permission.
 *
 * <p>For FOUND reports the pickup location and the exact coordinates are withheld from viewers who
 * have no approved claim — see {@code LostFoundAccessService.locationViewFor}.
 */
@Entity
@Table(name = "lost_found_items")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class LostFoundItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reported_by", nullable = false)
    private Long reportedBy;

    /**
     * Always derived server-side from the reporter's own affiliation, never from the request —
     * a lost item on campus X is noise on campus Y, so scoping is the default rather than an
     * opt-in. Null for reporters with no affiliation (Employer, Admin), which the feed predicate
     * treats as visible to everyone. See LostFoundAccessService.resolveItemUniversityId.
     */
    @Column(name = "university_id")
    private Long universityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 5)
    private LostFoundItemType itemType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 14)
    private LostFoundCategory category = LostFoundCategory.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 9)
    private LostFoundItemStatus status = LostFoundItemStatus.OPEN;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * The reporter's withheld adjudication secret — the detail a genuine owner would know and a
     * chancer would not. Rendered to the reporter and ADMINs only, never to a claimant at any
     * claim status.
     */
    @Column(name = "identifying_detail", length = 500)
    private String identifyingDetail;

    /** Bare object key (e.g. lost-found/9/uuid.jpeg) — never a resolved URL; see changeset 012. */
    @Column(name = "primary_image_key", length = 500)
    private String primaryImageKey;

    /** Human-readable label for the incident location — a map pin alone is useless indoors. */
    @Column(name = "incident_place", length = 255)
    private String incidentPlace;

    // BigDecimal, never Double: a double field renders `float`/`double precision`, which matches
    // neither the JDBC type code nor the `decimal` name prefix that Hibernate's schema validator
    // accepts, and would throw SchemaManagementException at context refresh — taking the entire
    // application down, not just this module. precision/scale are not themselves validated by
    // Hibernate 6, but they are what makes the generated DDL correct.
    // DECIMAL(10,7) = 3 integer digits (covers lat [-90,90] and lng [-180,180]) + ~1.1 cm of
    // resolution, far beyond any consumer GPS.
    @Column(name = "incident_latitude", precision = 10, scale = 7)
    private BigDecimal incidentLatitude;

    @Column(name = "incident_longitude", precision = 10, scale = 7)
    private BigDecimal incidentLongitude;

    /** Where the item can be collected. On a LOST report: where to return it to the reporter. */
    @Column(name = "pickup_place", length = 255)
    private String pickupPlace;

    @Column(name = "pickup_latitude", precision = 10, scale = 7)
    private BigDecimal pickupLatitude;

    @Column(name = "pickup_longitude", precision = 10, scale = 7)
    private BigDecimal pickupLongitude;

    @Column(name = "pickup_instructions", length = 500)
    private String pickupInstructions;

    /**
     * When the item was actually lost or found — deliberately not createdAt, which is when the
     * report was filed. A Monday loss is often reported on Wednesday, and the match scorer's date
     * term compares two occurredAt values; using createdAt would score reporting latency instead.
     */
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

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
    // loads the collections for up to 50 items per extra query, which keeps pagination in SQL.
    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @BatchSize(size = 50)
    private List<LostFoundItemMedia> media = new ArrayList<>();
}
