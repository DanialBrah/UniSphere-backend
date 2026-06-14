package com.unisphere.backend.identity.repository;

import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    Page<User> findByStatus(UserStatus status, Pageable pageable);

    // Search by display name or email across all role-specific tables.
    // Excludes the requesting user (excludeId) and soft-deleted accounts.
    @Query(value = """
            SELECT u.id FROM users u
            LEFT JOIN students  s  ON s.user_id  = u.id
            LEFT JOIN alumni    a  ON a.user_id  = u.id
            LEFT JOIN admins    ad ON ad.user_id = u.id
            LEFT JOIN employers e  ON e.user_id  = u.id
            LEFT JOIN universities un ON un.user_id = u.id
            LEFT JOIN clubs     c  ON c.user_id  = u.id
            WHERE u.deleted_at IS NULL
              AND u.id <> :excludeId
              AND (
                LOWER(COALESCE(s.full_name, a.full_name, ad.full_name,
                               e.company_name, un.name, c.name, ''))
                  LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            """,
            countQuery = """
            SELECT COUNT(u.id) FROM users u
            LEFT JOIN students  s  ON s.user_id  = u.id
            LEFT JOIN alumni    a  ON a.user_id  = u.id
            LEFT JOIN admins    ad ON ad.user_id = u.id
            LEFT JOIN employers e  ON e.user_id  = u.id
            LEFT JOIN universities un ON un.user_id = u.id
            LEFT JOIN clubs     c  ON c.user_id  = u.id
            WHERE u.deleted_at IS NULL
              AND u.id <> :excludeId
              AND (
                LOWER(COALESCE(s.full_name, a.full_name, ad.full_name,
                               e.company_name, un.name, c.name, ''))
                  LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            """,
            nativeQuery = true)
    Page<Long> searchIds(@Param("q") String q,
                         @Param("excludeId") Long excludeId,
                         Pageable pageable);

    // Admin: list all non-deleted users with optional role and status filters.
    // Passing null for either param disables that filter.
    @Query(value = """
            SELECT id FROM users
            WHERE deleted_at IS NULL
              AND (:role   IS NULL OR role   = :role)
              AND (:status IS NULL OR status = :status)
            """,
            countQuery = """
            SELECT COUNT(*) FROM users
            WHERE deleted_at IS NULL
              AND (:role   IS NULL OR role   = :role)
              AND (:status IS NULL OR status = :status)
            """,
            nativeQuery = true)
    Page<Long> listIds(@Param("role")   String role,
                       @Param("status") String status,
                       Pageable pageable);
}
