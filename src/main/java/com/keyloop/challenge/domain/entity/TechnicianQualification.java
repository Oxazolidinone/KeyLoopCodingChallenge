package com.keyloop.challenge.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "technician_qualifications",
        uniqueConstraints = @UniqueConstraint(columnNames = {"technicianId", "serviceTypeId"})
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TechnicianQualification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long technicianId;

    @Column(nullable = false)
    private Long serviceTypeId;

    public TechnicianQualification(Long technicianId, Long serviceTypeId) {
        this(null, technicianId, serviceTypeId);
    }
}
