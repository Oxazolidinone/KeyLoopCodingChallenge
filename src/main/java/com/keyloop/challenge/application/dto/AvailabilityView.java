package com.keyloop.challenge.application.dto;

import java.time.Instant;

public record AvailabilityView(
        boolean available,
        Long serviceBayId,
        Long technicianId,
        Instant start,
        Instant end
) {
}
