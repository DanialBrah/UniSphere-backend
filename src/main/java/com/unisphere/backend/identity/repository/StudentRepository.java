package com.unisphere.backend.identity.repository;

import com.unisphere.backend.identity.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {
}
