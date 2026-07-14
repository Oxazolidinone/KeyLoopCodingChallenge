package com.keyloop.challenge.domain.valueobject;

import java.time.Instant;

public record TimeWindow(Instant start, Instant end) {
    public TimeWindow {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Time window start and end are required");
        }
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Time window start must be before end");
        }
    }

    public boolean overlaps(TimeWindow other) {
        return start.isBefore(other.end) && end.isAfter(other.start);
    }
}
