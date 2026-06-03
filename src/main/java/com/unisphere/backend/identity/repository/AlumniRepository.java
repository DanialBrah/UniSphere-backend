package com.unisphere.backend.identity.repository;

import com.unisphere.backend.identity.entity.Alumni;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlumniRepository extends JpaRepository<Alumni, Long> {
}
