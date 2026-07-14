package com.keyloop.challenge.application.dto;

import java.time.Instant;

public record ScheduleAppointmentCommand(
        Long customerId,
        Long vehicleId,
        Long dealershipId,
        String serviceTypeCode,
        Instant requestedStart
) {
}
