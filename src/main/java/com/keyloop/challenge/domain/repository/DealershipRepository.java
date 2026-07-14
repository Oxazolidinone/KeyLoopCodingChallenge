package com.keyloop.challenge.domain.repository;

import com.keyloop.challenge.domain.entity.Dealership;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealershipRepository extends JpaRepository<Dealership, Long> {
}
