package com.unisphere.backend.identity.repository;

import com.unisphere.backend.identity.entity.University;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UniversityRepository extends JpaRepository<University, Long> {
}
