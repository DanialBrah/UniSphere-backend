package com.unisphere.backend.identity.repository;

import com.unisphere.backend.identity.entity.Employer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployerRepository extends JpaRepository<Employer, Long> {
}
