package com.keyloop.challenge.domain.repository;

import com.keyloop.challenge.domain.entity.Technician;
import com.keyloop.challenge.domain.valueobject.TechnicianCandidate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TechnicianRepository extends JpaRepository<Technician, Long> {
    @Query("""
            select new com.keyloop.challenge.domain.valueobject.TechnicianCandidate(t.id, count(q.id))
            from Technician t, TechnicianQualification q
            where t.dealershipId = :dealershipId
              and q.technicianId = t.id
              and exists (
                  select matched.id
                  from TechnicianQualification matched
                  where matched.technicianId = t.id
                    and matched.serviceTypeId = :serviceTypeId
              )
            group by t.id
            order by count(q.id), t.id
            """)
    List<TechnicianCandidate> findQualifiedCandidates(
            @Param("dealershipId") Long dealershipId,
            @Param("serviceTypeId") Long serviceTypeId
    );
}
