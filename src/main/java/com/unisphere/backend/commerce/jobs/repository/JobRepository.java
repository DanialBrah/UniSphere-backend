package com.unisphere.backend.commerce.jobs.repository;

import com.unisphere.backend.commerce.jobs.entity.Job;
import com.unisphere.backend.commerce.jobs.enums.ExperienceLevel;
import com.unisphere.backend.commerce.jobs.enums.JobStatus;
import com.unisphere.backend.commerce.jobs.enums.JobType;
import com.unisphere.backend.commerce.jobs.enums.WorkMode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {

    // Explicit null check needed since @SQLRestriction filters soft-deleted rows,
    // but we need to fetch by ID for the delete/update path before evaluating it.
    @Query("SELECT j FROM Job j WHERE j.id = :id AND j.deletedAt IS NULL")
    Optional<Job> findActiveById(@Param("id") Long id);

    /**
     * The browse feed. {@code status} is always a concrete, resolved value by the time this is
     * called — {@code JobService.getFeed} defaults a null filter to OPEN and rejects a
     * client-supplied DRAFT outright, so DRAFT can never reach this query.
     *
     * <p>isAdmin/viewerUniversityId are precomputed once per call in the service, so the visibility
     * predicate stays a plain column comparison and pagination totals remain exact.
     */
    @Query("""
            SELECT j FROM Job j
            WHERE j.status = :status
              AND ( :isAdmin = true
                    OR j.universityId IS NULL
                    OR j.universityId = :viewerUniversityId )
              AND (:jobType         IS NULL OR j.jobType         = :jobType)
              AND (:workMode        IS NULL OR j.workMode        = :workMode)
              AND (:experienceLevel IS NULL OR j.experienceLevel = :experienceLevel)
              AND (:universityId    IS NULL OR j.universityId    = :universityId)
            """)
    Page<Job> findFeed(@Param("isAdmin") boolean isAdmin,
                       @Param("viewerUniversityId") Long viewerUniversityId,
                       @Param("status") JobStatus status,
                       @Param("jobType") JobType jobType,
                       @Param("workMode") WorkMode workMode,
                       @Param("experienceLevel") ExperienceLevel experienceLevel,
                       @Param("universityId") Long universityId,
                       Pageable pageable);

    /** The caller's own jobs, every status — no visibility predicate, they are all theirs. */
    @Query("""
            SELECT j FROM Job j
            WHERE j.employerId = :employerId
              AND (:status IS NULL OR j.status = :status)
            """)
    Page<Job> findByEmployerId(@Param("employerId") Long employerId,
                               @Param("status") JobStatus status,
                               Pageable pageable);

    /**
     * FULLTEXT search over title and description. The service sanitises the query first: an
     * unbalanced BOOLEAN-MODE operator is a MySQL syntax error that would surface as a 500 on a
     * plain user search. Native query, so deleted_at/status are hand-written and the caller strips
     * any client-supplied sort — same three obligations as EventRepository.searchFullText.
     */
    @Query(value = """
            SELECT * FROM jobs j
            WHERE MATCH(j.title, j.description) AGAINST (:query IN BOOLEAN MODE)
              AND j.deleted_at IS NULL
              AND j.status = 'OPEN'
              AND ( :isAdmin = true OR j.university_id IS NULL OR j.university_id = :viewerUniversityId )
              AND (:jobType IS NULL OR j.job_type = :jobType)
            ORDER BY j.created_at DESC, j.id DESC
            """,
           countQuery = """
            SELECT COUNT(*) FROM jobs j
            WHERE MATCH(j.title, j.description) AGAINST (:query IN BOOLEAN MODE)
              AND j.deleted_at IS NULL
              AND j.status = 'OPEN'
              AND ( :isAdmin = true OR j.university_id IS NULL OR j.university_id = :viewerUniversityId )
              AND (:jobType IS NULL OR j.job_type = :jobType)
            """,
           nativeQuery = true)
    Page<Job> searchFullText(@Param("query") String query,
                             @Param("isAdmin") boolean isAdmin,
                             @Param("viewerUniversityId") Long viewerUniversityId,
                             @Param("jobType") String jobType,
                             Pageable pageable);

    /**
     * {@code JobDeadlineScheduler}'s sweep: ages every OPEN job past its application deadline to
     * CLOSED in one statement.
     *
     * <p>A bulk UPDATE rather than select-then-save: the WHERE clause carries {@code status = OPEN},
     * so a second Render replica running the same tick matches zero rows the second time — safe with
     * no distributed lock. {@code @SQLRestriction} is NOT applied to bulk HQL updates, so
     * {@code deletedAt IS NULL} is written by hand, and JPA auditing does not fire on bulk updates
     * either, so {@code updatedAt} is set explicitly. Mirrors {@code EventRepository.completeEndedEvents}.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Job j
               SET j.status    = com.unisphere.backend.commerce.jobs.enums.JobStatus.CLOSED,
                   j.updatedAt = :now
             WHERE j.status    = com.unisphere.backend.commerce.jobs.enums.JobStatus.OPEN
               AND j.applicationDeadline < :today
               AND j.deletedAt IS NULL
            """)
    int closeExpiredJobs(@Param("now") LocalDateTime now, @Param("today") LocalDate today);
}
