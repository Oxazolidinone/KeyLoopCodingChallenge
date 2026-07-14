package com.keyloop.challenge.application.dto;

import com.keyloop.challenge.domain.entity.AppointmentStatus;
import java.time.Instant;
import java.util.UUID;

public record AppointmentView(
        UUID id,
        Long customerId,
        Long vehicleId,
        Long dealershipId,
        String serviceTypeCode,
        Long technicianId,
        Long serviceBayId,
        Instant start,
        Instant end,
        AppointmentStatus status
) {
}
