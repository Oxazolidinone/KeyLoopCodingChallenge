package com.keyloop.challenge.domain.repository;

import com.keyloop.challenge.domain.entity.Appointment;
import com.keyloop.challenge.domain.entity.AppointmentStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    @Query("""
            select a
            from Appointment a
            where a.dealershipId = :dealershipId
              and a.status = :status
              and a.startTime < :end
              and a.endTime > :start
            order by a.startTime, a.id
            """)
    List<Appointment> findOverlappingAppointments(
            @Param("dealershipId") Long dealershipId,
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("status") AppointmentStatus status
    );
}
