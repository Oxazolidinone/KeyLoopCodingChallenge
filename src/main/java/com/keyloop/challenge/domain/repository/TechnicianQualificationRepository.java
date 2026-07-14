package com.keyloop.challenge.domain.repository;

import com.keyloop.challenge.domain.entity.TechnicianQualification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechnicianQualificationRepository extends JpaRepository<TechnicianQualification, Long> {
}
