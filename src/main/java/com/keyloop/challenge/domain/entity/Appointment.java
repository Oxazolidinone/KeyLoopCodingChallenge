package com.keyloop.challenge.domain.entity;

import com.keyloop.challenge.domain.valueobject.ResourceAssignment;
import com.keyloop.challenge.domain.valueobject.TimeWindow;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "appointments",
        indexes = {
                @Index(name = "idx_appointments_dealer_time", columnList = "dealershipId,startTime,endTime"),
                @Index(name = "idx_appointments_bay_time", columnList = "serviceBayId,startTime,endTime"),
                @Index(name = "idx_appointments_tech_time", columnList = "technicianId,startTime,endTime")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Appointment {
    @Id
    private UUID id;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private Long vehicleId;

    @Column(nullable = false)
    private Long dealershipId;

    @Column(nullable = false)
    private Long serviceTypeId;

    @Column(nullable = false)
    private String serviceTypeCode;

    @Column(nullable = false)
    private Long technicianId;

    @Column(nullable = false)
    private Long serviceBayId;

    @Column(nullable = false)
    private Instant startTime;

    @Column(nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    public static Appointment confirmed(
            Long customerId,
            Long vehicleId,
            Long dealershipId,
            ServiceType serviceType,
            ResourceAssignment assignment,
            TimeWindow slot,
            Instant createdAt
    ) {
        return new Appointment(
                UUID.randomUUID(),
                customerId,
                vehicleId,
                dealershipId,
                serviceType.getId(),
                serviceType.getCode(),
                assignment.technicianId(),
                assignment.serviceBayId(),
                slot.start(),
                slot.end(),
                AppointmentStatus.CONFIRMED,
                createdAt
        );
    }
}
