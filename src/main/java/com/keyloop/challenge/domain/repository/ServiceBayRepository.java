package com.keyloop.challenge.domain.repository;

import com.keyloop.challenge.domain.entity.AppointmentStatus;
import com.keyloop.challenge.domain.entity.ServiceBay;
import com.keyloop.challenge.domain.valueobject.ServiceBayCandidate;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceBayRepository extends JpaRepository<ServiceBay, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b
            from ServiceBay b
            where b.dealershipId = :dealershipId
            order by b.id
            """)
    List<ServiceBay> findAllForUpdateByDealershipId(@Param("dealershipId") Long dealershipId);

    @Query("""
            select new com.keyloop.challenge.domain.valueobject.ServiceBayCandidate(b.id, count(a.id))
            from ServiceBay b
            left join Appointment a on a.serviceBayId = b.id and a.status = :status
            where b.dealershipId = :dealershipId
            group by b.id
            order by count(a.id), b.id
            """)
    List<ServiceBayCandidate> findCandidatesByFit(
            @Param("dealershipId") Long dealershipId,
            @Param("status") AppointmentStatus status
    );
}
