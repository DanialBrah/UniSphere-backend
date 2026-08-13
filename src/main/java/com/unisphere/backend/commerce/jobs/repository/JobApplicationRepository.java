package com.unisphere.backend.commerce.jobs.repository;

import com.unisphere.backend.commerce.jobs.entity.JobApplication;
import com.unisphere.backend.commerce.jobs.enums.JobApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    boolean existsByJobIdAndApplicantId(Long jobId, Long applicantId);

    Optional<JobApplication> findByJobIdAndApplicantId(Long jobId, Long applicantId);

    /** Applicant roster for one job, optionally filtered by status. */
    @Query("""
            SELECT a FROM JobApplication a
            WHERE a.jobId = :jobId
              AND (:status IS NULL OR a.status = :status)
            """)
    Page<JobApplication> findByJob(@Param("jobId") Long jobId,
                                   @Param("status") JobApplicationStatus status,
                                   Pageable pageable);

    /** "My applications" across every job, optionally filtered by status. */
    @Query("""
            SELECT a FROM JobApplication a
            WHERE a.applicantId = :applicantId
              AND (:status IS NULL OR a.status = :status)
            """)
    Page<JobApplication> findByApplicant(@Param("applicantId") Long applicantId,
                                         @Param("status") JobApplicationStatus status,
                                         Pageable pageable);

    /** Backs {@code JobStatsResponse} — one grouped query, zero-filled in Java via EnumMap. */
    @Query("""
            SELECT a.status AS status, COUNT(a) AS total FROM JobApplication a
            WHERE a.jobId = :jobId
            GROUP BY a.status
            """)
    List<StatusCount> countByJobIdGroupByStatus(@Param("jobId") Long jobId);

    interface StatusCount {
        JobApplicationStatus getStatus();
        long getTotal();
    }

    /**
     * The feed's batch "did the viewer apply, and with what status" lookup — one query for the whole
     * page, loaded into a {@code Map<jobId, status>} rather than once per row.
     */
    List<JobApplication> findByJobIdInAndApplicantId(Collection<Long> jobIds, Long applicantId);

    /** {@code JobService.notifyApplicantsOfClosure}'s fan-out list — ids only, no need to hydrate full rows. */
    @Query("""
            SELECT a.applicantId FROM JobApplication a
            WHERE a.jobId = :jobId AND a.status IN :statuses
            """)
    List<Long> findApplicantIdsByJobIdAndStatusIn(@Param("jobId") Long jobId,
                                                  @Param("statuses") Collection<JobApplicationStatus> statuses);
}
