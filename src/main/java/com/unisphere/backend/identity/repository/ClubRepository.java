package com.unisphere.backend.identity.repository;

import com.unisphere.backend.identity.entity.Club;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClubRepository extends JpaRepository<Club, Long> {
}
