package com.unisphere.backend.projects.repository;

import com.unisphere.backend.projects.entity.ProjectRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectRoleRepository extends JpaRepository<ProjectRole, Long> {

    List<ProjectRole> findByProjectIdOrderByCreatedAtAsc(Long projectId);

    Optional<ProjectRole> findByIdAndProjectId(Long id, Long projectId);

    /** Batch open-role-count lookup for a page of projects — one query, not one per row. */
    @Query("""
            SELECT r.projectId AS projectId, COUNT(r) AS total FROM ProjectRole r
            WHERE r.projectId IN :projectIds
              AND r.status = com.unisphere.backend.projects.enums.ProjectRoleStatus.OPEN
            GROUP BY r.projectId
            """)
    List<ProjectCount> countOpenByProjectIdIn(@Param("projectIds") Collection<Long> projectIds);

    interface ProjectCount {
        Long getProjectId();
        long getTotal();
    }
}
