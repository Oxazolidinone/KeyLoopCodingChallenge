package com.keyloop.challenge.middleware.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record ScheduleAppointmentRequest(
        @NotNull Long customerId,
        @NotNull Long vehicleId,
        @NotNull Long dealershipId,
        @NotBlank String serviceTypeCode,
        @NotNull OffsetDateTime requestedStart
) {
}
