package com.keyloop.challenge.domain.valueobject;

public record AvailabilityResult(boolean available, ResourceAssignment assignment, TimeWindow slot) {
    public static AvailabilityResult available(ResourceAssignment assignment, TimeWindow slot) {
        return new AvailabilityResult(true, assignment, slot);
    }

    public static AvailabilityResult unavailable(TimeWindow slot) {
        return new AvailabilityResult(false, null, slot);
    }
}
