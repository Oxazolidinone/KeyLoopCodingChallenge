package com.keyloop.challenge.domain.repository;

import com.keyloop.challenge.domain.entity.ServiceType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceTypeRepository extends JpaRepository<ServiceType, Long> {
    Optional<ServiceType> findByCode(String code);
}
