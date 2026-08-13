package com.unisphere.backend.projects.repository;

import com.unisphere.backend.projects.entity.ProjectMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    Page<ProjectMember> findByProjectId(Long projectId, Pageable pageable);

    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);

    boolean existsByProjectIdAndUserId(Long projectId, Long userId);

    void deleteByProjectIdAndUserId(Long projectId, Long userId);

    long countByProjectId(Long projectId);

    /** Batch member-count lookup for a page of projects — one query, not one per row. */
    @Query("""
            SELECT m.projectId AS projectId, COUNT(m) AS total FROM ProjectMember m
            WHERE m.projectId IN :projectIds
            GROUP BY m.projectId
            """)
    List<ProjectCount> countByProjectIdIn(@Param("projectIds") Collection<Long> projectIds);

    interface ProjectCount {
        Long getProjectId();
        long getTotal();
    }
}
