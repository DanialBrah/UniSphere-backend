package com.unisphere.backend.campus.lostfound.repository;

import com.unisphere.backend.campus.lostfound.entity.LostFoundItem;
import com.unisphere.backend.campus.lostfound.enums.LostFoundCategory;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemStatus;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LostFoundItemRepository extends JpaRepository<LostFoundItem, Long> {

    // Explicit null check needed since @SQLRestriction filters soft-deleted rows,
    // but we need to fetch by ID for the delete operation before setting deleted_at
    @Query("SELECT i FROM LostFoundItem i WHERE i.id = :id AND i.deletedAt IS NULL")
    Optional<LostFoundItem> findActiveById(@Param("id") Long id);

    /**
     * The board, with optional type/status/category facets. isAdmin/viewerUniversityId are
     * precomputed once per call in the service, so the visibility predicate stays a plain column
     * comparison and pagination totals remain exact.
     *
     * <p>Items with a null universityId are visible to everyone — that is what a report filed by an
     * unaffiliated account (Employer, Admin) looks like.
     *
     * <p>No ORDER BY here on purpose: ordering comes from {@code @PageableDefault} on the
     * controller, so a query-embedded clause cannot silently end up merely a tiebreaker behind
     * Spring's appended one.
     */
    @Query("""
            SELECT i FROM LostFoundItem i
            WHERE ( :isAdmin = true
                    OR i.universityId IS NULL
                    OR i.universityId = :viewerUniversityId )
              AND (:itemType IS NULL OR i.itemType = :itemType)
              AND (:status   IS NULL OR i.status   = :status)
              AND (:category IS NULL OR i.category = :category)
            """)
    Page<LostFoundItem> findFeed(@Param("isAdmin") boolean isAdmin,
                                 @Param("viewerUniversityId") Long viewerUniversityId,
                                 @Param("itemType") LostFoundItemType itemType,
                                 @Param("status") LostFoundItemStatus status,
                                 @Param("category") LostFoundCategory category,
                                 Pageable pageable);

    /** The caller's own reports, every status — no visibility predicate, they are all theirs. */
    @Query("""
            SELECT i FROM LostFoundItem i
            WHERE i.reportedBy = :reportedBy
              AND (:status IS NULL OR i.status = :status)
            """)
    Page<LostFoundItem> findByReporter(@Param("reportedBy") Long reportedBy,
                                       @Param("status") LostFoundItemStatus status,
                                       Pageable pageable);

    /**
     * Map viewport query. JPQL rather than native because the filter is pure column comparison with
     * no trigonometry, so {@code idx_lfitem_geo} is used, {@code @SQLRestriction} applies
     * automatically, and enum parameters bind by name rather than by ordinal.
     *
     * <p>Returns an interface projection, not entities: Hibernate then selects nine columns instead
     * of hydrating entities and their {@code @BatchSize} media collections, which is what makes a
     * 500-pin viewport cheap. The service passes an unsorted {@code PageRequest.of(0, cap)} — there
     * is no count query and no Page, because a map client re-queries on pan rather than paginating.
     */
    @Query("""
            SELECT i.id                AS id,
                   i.itemType          AS itemType,
                   i.status            AS status,
                   i.category          AS category,
                   i.title             AS title,
                   i.reportedBy        AS reportedBy,
                   i.primaryImageKey   AS primaryImageKey,
                   i.incidentLatitude  AS latitude,
                   i.incidentLongitude AS longitude
            FROM LostFoundItem i
            WHERE i.incidentLatitude  BETWEEN :minLat AND :maxLat
              AND i.incidentLongitude BETWEEN :minLng AND :maxLng
              AND ( :isAdmin = true
                    OR i.universityId IS NULL
                    OR i.universityId = :viewerUniversityId )
              AND (:itemType IS NULL OR i.itemType = :itemType)
              AND (:status   IS NULL OR i.status   = :status)
            ORDER BY i.id DESC
            """)
    List<MapPinView> findInViewport(@Param("minLat") BigDecimal minLat,
                                    @Param("maxLat") BigDecimal maxLat,
                                    @Param("minLng") BigDecimal minLng,
                                    @Param("maxLng") BigDecimal maxLng,
                                    @Param("isAdmin") boolean isAdmin,
                                    @Param("viewerUniversityId") Long viewerUniversityId,
                                    @Param("itemType") LostFoundItemType itemType,
                                    @Param("status") LostFoundItemStatus status,
                                    Pageable pageable);

    /** Projection for {@link #findInViewport} — the nine columns a map pin needs, nothing else. */
    interface MapPinView {
        Long getId();
        LostFoundItemType getItemType();
        LostFoundItemStatus getStatus();
        LostFoundCategory getCategory();
        String getTitle();
        Long getReportedBy();
        String getPrimaryImageKey();
        BigDecimal getLatitude();
        BigDecimal getLongitude();
    }

    // ── Stats ───────────────────────────────────────────────────────────────────────────────────
    // Three typed projections rather than List<Object[]> so the service never casts.

    @Query("""
            SELECT i.itemType AS itemType, COUNT(i) AS total FROM LostFoundItem i
            WHERE (:isAdmin = true OR i.universityId IS NULL OR i.universityId = :viewerUniversityId)
            GROUP BY i.itemType
            """)
    List<TypeCount> countByType(@Param("isAdmin") boolean isAdmin,
                                @Param("viewerUniversityId") Long viewerUniversityId);

    @Query("""
            SELECT i.status AS status, COUNT(i) AS total FROM LostFoundItem i
            WHERE (:isAdmin = true OR i.universityId IS NULL OR i.universityId = :viewerUniversityId)
            GROUP BY i.status
            """)
    List<StatusCount> countByStatus(@Param("isAdmin") boolean isAdmin,
                                    @Param("viewerUniversityId") Long viewerUniversityId);

    @Query("""
            SELECT i.category AS category, COUNT(i) AS total FROM LostFoundItem i
            WHERE (:isAdmin = true OR i.universityId IS NULL OR i.universityId = :viewerUniversityId)
            GROUP BY i.category
            """)
    List<CategoryCount> countByCategory(@Param("isAdmin") boolean isAdmin,
                                        @Param("viewerUniversityId") Long viewerUniversityId);

    interface TypeCount {
        LostFoundItemType getItemType();
        long getTotal();
    }

    interface StatusCount {
        LostFoundItemStatus getStatus();
        long getTotal();
    }

    interface CategoryCount {
        LostFoundCategory getCategory();
        long getTotal();
    }

    /**
     * Ages every stale OPEN report out of the board in one statement.
     *
     * <p>A bulk UPDATE rather than select-then-save: the WHERE clause carries status = OPEN, so a
     * second replica running the same tick matches zero rows instead of double-expiring — safe on
     * every instance with no distributed lock.
     *
     * <p>Two non-obvious details, both inherited from {@code NewsArticleRepository.publishDueArticles}.
     * {@code @SQLRestriction} is NOT applied to bulk HQL updates, so {@code deletedAt IS NULL} is
     * mandatory or a soft-deleted item resurrects itself as EXPIRED. And JPA auditing does not fire
     * on bulk updates, so updatedAt is set explicitly.
     *
     * <p>{@code cutoff} is a parameter rather than computed inside the query so that tests can pass
     * a future cutoff to age fresh rows — {@code createdAt} is {@code @CreatedDate updatable = false}
     * and cannot be backdated.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE LostFoundItem i
               SET i.status    = com.unisphere.backend.campus.lostfound.enums.LostFoundItemStatus.EXPIRED,
                   i.updatedAt = :now
             WHERE i.status    = com.unisphere.backend.campus.lostfound.enums.LostFoundItemStatus.OPEN
               AND i.createdAt <= :cutoff
               AND i.deletedAt IS NULL
            """)
    int expireStaleItems(@Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);

    // ── Native queries ──────────────────────────────────────────────────────────────────────────
    // All three below share three obligations:
    //   1. deleted_at IS NULL by hand — native SQL bypasses @SQLRestriction.
    //   2. Enum parameters typed String — Hibernate binds a Java enum to a native :param by its
    //      ORDINAL, which would silently filter on 1 instead of 'FOUND'. The service passes .name().
    //   3. The caller strips any client-supplied sort — Spring appends the raw property name to
    //      native SQL, so ?sort=createdAt emits ORDER BY createdAt against a created_at column.

    /**
     * FULLTEXT search over title and description.
     *
     * <p>The service sanitises the query first: an unbalanced BOOLEAN-MODE operator is a MySQL
     * syntax error that would surface as a 500 on a plain user search. Note also that
     * {@code innodb_ft_min_token_size} defaults to 3, so one- and two-character terms match nothing
     * regardless of the data.
     */
    @Query(value = """
            SELECT * FROM lost_found_items i
            WHERE MATCH(i.title, i.description) AGAINST (:query IN BOOLEAN MODE)
              AND i.deleted_at IS NULL
              AND ( :isAdmin = true OR i.university_id IS NULL OR i.university_id = :viewerUniversityId )
              AND (:itemType IS NULL OR i.item_type = :itemType)
              AND (:status   IS NULL OR i.status    = :status)
            ORDER BY i.created_at DESC, i.id DESC
            """,
           countQuery = """
            SELECT COUNT(*) FROM lost_found_items i
            WHERE MATCH(i.title, i.description) AGAINST (:query IN BOOLEAN MODE)
              AND i.deleted_at IS NULL
              AND ( :isAdmin = true OR i.university_id IS NULL OR i.university_id = :viewerUniversityId )
              AND (:itemType IS NULL OR i.item_type = :itemType)
              AND (:status   IS NULL OR i.status    = :status)
            """,
           nativeQuery = true)
    Page<LostFoundItem> searchFullText(@Param("query") String query,
                                       @Param("isAdmin") boolean isAdmin,
                                       @Param("viewerUniversityId") Long viewerUniversityId,
                                       @Param("itemType") String itemType,
                                       @Param("status") String status,
                                       Pageable pageable);

    /**
     * Radius search around a point, in two stages.
     *
     * <p><b>Stage 1 — the four BETWEEN predicates.</b> Plain column comparisons, so MySQL
     * range-scans {@code idx_lfitem_geo}. The bounding-box deltas are computed in Java by the
     * service, never in SQL, so nothing here is per-row and nothing defeats the index.
     *
     * <p><b>Stage 2 — the Haversine expression.</b> A bounding box is a square and a radius is the
     * inscribed circle, so up to 21% of stage-1 hits sit outside the radius — the corners. The
     * {@code <= :radiusKm} clause trims them and the ORDER BY sorts the rest by true great-circle
     * distance. 6371 is the mean Earth radius in km. {@code i.id ASC} is the tiebreaker so two
     * items at identical coordinates cannot swap places between pages.
     *
     * <p>Returns entities rather than a distance projection on purpose: the service recomputes
     * Haversine in Java for the <= 20 rows on the page (a handful of trig ops, free) which keeps the
     * entity mapping and PageContext batch loading unchanged. A projection would have forced a
     * second fetch to render each item.
     */
    @Query(value = """
            SELECT * FROM lost_found_items i
            WHERE i.deleted_at IS NULL
              AND i.incident_latitude  BETWEEN :minLat AND :maxLat
              AND i.incident_longitude BETWEEN :minLng AND :maxLng
              AND ( :isAdmin = true OR i.university_id IS NULL OR i.university_id = :viewerUniversityId )
              AND (:itemType IS NULL OR i.item_type = :itemType)
              AND (:status   IS NULL OR i.status    = :status)
              AND (:category IS NULL OR i.category  = :category)
              AND ( 6371 * 2 * ASIN(SQRT(
                      POWER(SIN(RADIANS(:lat - i.incident_latitude) / 2), 2)
                    + COS(RADIANS(:lat)) * COS(RADIANS(i.incident_latitude))
                    * POWER(SIN(RADIANS(:lng - i.incident_longitude) / 2), 2)
                  )) ) <= :radiusKm
            ORDER BY ( 6371 * 2 * ASIN(SQRT(
                      POWER(SIN(RADIANS(:lat - i.incident_latitude) / 2), 2)
                    + COS(RADIANS(:lat)) * COS(RADIANS(i.incident_latitude))
                    * POWER(SIN(RADIANS(:lng - i.incident_longitude) / 2), 2)
                  )) ) ASC, i.id ASC
            """,
           countQuery = """
            SELECT COUNT(*) FROM lost_found_items i
            WHERE i.deleted_at IS NULL
              AND i.incident_latitude  BETWEEN :minLat AND :maxLat
              AND i.incident_longitude BETWEEN :minLng AND :maxLng
              AND ( :isAdmin = true OR i.university_id IS NULL OR i.university_id = :viewerUniversityId )
              AND (:itemType IS NULL OR i.item_type = :itemType)
              AND (:status   IS NULL OR i.status    = :status)
              AND (:category IS NULL OR i.category  = :category)
              AND ( 6371 * 2 * ASIN(SQRT(
                      POWER(SIN(RADIANS(:lat - i.incident_latitude) / 2), 2)
                    + COS(RADIANS(:lat)) * COS(RADIANS(i.incident_latitude))
                    * POWER(SIN(RADIANS(:lng - i.incident_longitude) / 2), 2)
                  )) ) <= :radiusKm
            """,
           nativeQuery = true)
    Page<LostFoundItem> findNearby(@Param("lat") BigDecimal lat,
                                   @Param("lng") BigDecimal lng,
                                   @Param("minLat") BigDecimal minLat,
                                   @Param("maxLat") BigDecimal maxLat,
                                   @Param("minLng") BigDecimal minLng,
                                   @Param("maxLng") BigDecimal maxLng,
                                   @Param("radiusKm") double radiusKm,
                                   @Param("isAdmin") boolean isAdmin,
                                   @Param("viewerUniversityId") Long viewerUniversityId,
                                   @Param("itemType") String itemType,
                                   @Param("status") String status,
                                   @Param("category") String category,
                                   Pageable pageable);

    /**
     * Counterpart candidates for the auto-matcher, pre-filtered and pre-ranked in SQL so the
     * {@code LIMIT} cuts the right rows before Java scoring refines the order.
     *
     * <p>NATURAL LANGUAGE MODE, not BOOLEAN: the source item's own title is fed in verbatim, and
     * BOOLEAN mode would choke on any {@code +}, {@code -} or {@code "} the reporter typed. The
     * relevance term appears only in ORDER BY, so this stays a plain entity query — no projection,
     * no {@code @SqlResultSetMapping}.
     *
     * <p>The geo and university predicates are written {@code (:param IS NULL OR column IS NULL OR ...)}
     * so items with no coordinates survive the filter and get scored down in Java instead of being
     * dropped. A wallet described only in text is still a valid match.
     */
    @Query(value = """
            SELECT * FROM lost_found_items i
            WHERE i.deleted_at IS NULL
              AND i.item_type   = :counterpartType
              AND i.status      = 'OPEN'
              AND i.reported_by <> :reporterId
              AND i.occurred_at BETWEEN :fromDate AND :toDate
              AND (:universityId IS NULL OR i.university_id IS NULL OR i.university_id = :universityId)
              AND (:minLat IS NULL OR i.incident_latitude  IS NULL OR i.incident_latitude  BETWEEN :minLat AND :maxLat)
              AND (:minLng IS NULL OR i.incident_longitude IS NULL OR i.incident_longitude BETWEEN :minLng AND :maxLng)
            ORDER BY MATCH(i.title, i.description) AGAINST (:query IN NATURAL LANGUAGE MODE) DESC,
                     i.occurred_at DESC
            """,
           nativeQuery = true)
    List<LostFoundItem> findMatchCandidates(@Param("counterpartType") String counterpartType,
                                            @Param("reporterId") Long reporterId,
                                            @Param("fromDate") LocalDateTime fromDate,
                                            @Param("toDate") LocalDateTime toDate,
                                            @Param("universityId") Long universityId,
                                            @Param("minLat") BigDecimal minLat,
                                            @Param("maxLat") BigDecimal maxLat,
                                            @Param("minLng") BigDecimal minLng,
                                            @Param("maxLng") BigDecimal maxLng,
                                            @Param("query") String query,
                                            Pageable pageable);
}
