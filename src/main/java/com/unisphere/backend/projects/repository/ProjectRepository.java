package com.unisphere.backend.projects.repository;

import com.unisphere.backend.projects.entity.Project;
import com.unisphere.backend.projects.enums.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    // Explicit null check needed since @SQLRestriction filters soft-deleted rows,
    // but we need to fetch by ID for the update/delete path before evaluating it.
    @Query("SELECT p FROM Project p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Project> findActiveById(@Param("id") Long id);

    /**
     * The browse feed. isAdmin/viewerUniversityId are precomputed once per call in the service, so
     * the visibility predicate stays a plain column comparison and pagination totals remain exact —
     * same shape as {@code JobRepository.findFeed}.
     */
    @Query("""
            SELECT p FROM Project p
            WHERE ( :isAdmin = true OR p.universityId IS NULL OR p.universityId = :viewerUniversityId )
              AND (:status       IS NULL OR p.status       = :status)
              AND (:recruiting   IS NULL OR p.recruiting    = :recruiting)
              AND (:universityId IS NULL OR p.universityId = :universityId)
              AND (:ownerId      IS NULL OR p.ownerId       = :ownerId)
            """)
    Page<Project> findFeed(@Param("isAdmin") boolean isAdmin,
                           @Param("viewerUniversityId") Long viewerUniversityId,
                           @Param("status") ProjectStatus status,
                           @Param("recruiting") Boolean recruiting,
                           @Param("universityId") Long universityId,
                           @Param("ownerId") Long ownerId,
                           Pageable pageable);

    /** The caller's own projects, every status — no visibility predicate, they are all theirs. */
    @Query("""
            SELECT p FROM Project p
            WHERE p.ownerId = :ownerId
              AND (:status IS NULL OR p.status = :status)
            """)
    Page<Project> findByOwnerId(@Param("ownerId") Long ownerId,
                                @Param("status") ProjectStatus status,
                                Pageable pageable);

    /** Projects the caller has joined as a non-owner contributor. */
    @Query("""
            SELECT p FROM Project p
            WHERE p.id IN (
                SELECT m.projectId FROM ProjectMember m
                WHERE m.userId = :userId
                  AND m.role <> com.unisphere.backend.projects.enums.ProjectMemberRole.OWNER
            )
            """)
    Page<Project> findJoinedByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * FULLTEXT search over title and description. The service sanitises the query first: an
     * unbalanced BOOLEAN-MODE operator is a MySQL syntax error that would surface as a 500 on a
     * plain user search. Native query, so deleted_at is hand-written and the caller strips any
     * client-supplied sort — same obligations as {@code JobRepository.searchFullText}.
     */
    @Query(value = """
            SELECT * FROM projects p
            WHERE MATCH(p.title, p.description) AGAINST (:query IN BOOLEAN MODE)
              AND p.deleted_at IS NULL
              AND ( :isAdmin = true OR p.university_id IS NULL OR p.university_id = :viewerUniversityId )
            ORDER BY p.created_at DESC, p.id DESC
            """,
           countQuery = """
            SELECT COUNT(*) FROM projects p
            WHERE MATCH(p.title, p.description) AGAINST (:query IN BOOLEAN MODE)
              AND p.deleted_at IS NULL
              AND ( :isAdmin = true OR p.university_id IS NULL OR p.university_id = :viewerUniversityId )
            """,
           nativeQuery = true)
    Page<Project> searchFullText(@Param("query") String query,
                                 @Param("isAdmin") boolean isAdmin,
                                 @Param("viewerUniversityId") Long viewerUniversityId,
                                 Pageable pageable);
}
