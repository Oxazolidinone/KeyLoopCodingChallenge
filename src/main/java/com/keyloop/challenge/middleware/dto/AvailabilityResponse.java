package com.keyloop.challenge.middleware.dto;

import java.time.Instant;

public record AvailabilityResponse(
        boolean available,
        Long serviceBayId,
        Long technicianId,
        Instant start,
        Instant end
) {
}
