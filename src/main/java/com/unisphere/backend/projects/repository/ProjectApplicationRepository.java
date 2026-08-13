package com.unisphere.backend.projects.repository;

import com.unisphere.backend.projects.entity.ProjectApplication;
import com.unisphere.backend.projects.enums.ProjectApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProjectApplicationRepository extends JpaRepository<ProjectApplication, Long> {

    boolean existsByProjectRoleIdAndApplicantId(Long projectRoleId, Long applicantId);

    /** Guards {@code ProjectService.deleteRole} — a role with any application (of any status) keeps its audit trail. */
    boolean existsByProjectRoleId(Long projectRoleId);

    Optional<ProjectApplication> findByProjectRoleIdAndApplicantId(Long projectRoleId, Long applicantId);

    /** Applicant roster across every role on one project, optionally filtered by role and/or status. */
    @Query("""
            SELECT a FROM ProjectApplication a
            WHERE a.projectId = :projectId
              AND (:projectRoleId IS NULL OR a.projectRoleId = :projectRoleId)
              AND (:status IS NULL OR a.status = :status)
            """)
    Page<ProjectApplication> findByProject(@Param("projectId") Long projectId,
                                           @Param("projectRoleId") Long projectRoleId,
                                           @Param("status") ProjectApplicationStatus status,
                                           Pageable pageable);

    /** "My applications" across every project, optionally filtered by status. */
    @Query("""
            SELECT a FROM ProjectApplication a
            WHERE a.applicantId = :applicantId
              AND (:status IS NULL OR a.status = :status)
            """)
    Page<ProjectApplication> findByApplicant(@Param("applicantId") Long applicantId,
                                             @Param("status") ProjectApplicationStatus status,
                                             Pageable pageable);
}
