package com.keyloop.challenge.application.dto;

import java.time.Instant;

public record AvailabilityQuery(
        Long dealershipId,
        String serviceTypeCode,
        Instant requestedStart
) {
}
