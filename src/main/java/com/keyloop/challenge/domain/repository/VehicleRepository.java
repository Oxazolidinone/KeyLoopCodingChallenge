package com.keyloop.challenge.domain.repository;

import com.keyloop.challenge.domain.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
}
