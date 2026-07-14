package com.keyloop.challenge.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.keyloop.challenge.domain.entity.Appointment;
import com.keyloop.challenge.domain.entity.AppointmentStatus;
import com.keyloop.challenge.domain.valueobject.ResourceAssignment;
import com.keyloop.challenge.domain.valueobject.ServiceBayCandidate;
import com.keyloop.challenge.domain.valueobject.TechnicianCandidate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SkillFitPolicyTest {
    private final SkillFitPolicy policy = new SkillFitPolicy();

    @Test
    void selectsLeastLoadedBayAndLeastSkilledQualifiedTechnician() {
        Optional<ResourceAssignment> assignment = policy.select(
                List.of(new ServiceBayCandidate(1L, 4L), new ServiceBayCandidate(2L, 1L)),
                List.of(new TechnicianCandidate(1L, 2L), new TechnicianCandidate(3L, 1L)),
                List.of()
        );

        assertTrue(assignment.isPresent());
        assertEquals(2L, assignment.get().serviceBayId());
        assertEquals(3L, assignment.get().technicianId());
    }

    @Test
    void skipsResourcesThatOverlapWithExistingAppointments() {
        Optional<ResourceAssignment> assignment = policy.select(
                List.of(new ServiceBayCandidate(1L, 4L), new ServiceBayCandidate(2L, 1L)),
                List.of(new TechnicianCandidate(1L, 2L), new TechnicianCandidate(3L, 1L)),
                List.of(appointment(2L, 3L))
        );

        assertTrue(assignment.isPresent());
        assertEquals(1L, assignment.get().serviceBayId());
        assertEquals(1L, assignment.get().technicianId());
    }

    @Test
    void returnsEmptyWhenEitherBayOrTechnicianIsUnavailable() {
        Optional<ResourceAssignment> assignment = policy.select(
                List.of(new ServiceBayCandidate(1L, 0L)),
                List.of(new TechnicianCandidate(3L, 1L)),
                List.of(appointment(1L, 3L))
        );

        assertTrue(assignment.isEmpty());
    }

    private Appointment appointment(Long serviceBayId, Long technicianId) {
        Instant start = Instant.parse("2026-07-15T09:00:00Z");
        return new Appointment(
                UUID.randomUUID(),
                1L,
                1L,
                1L,
                1L,
                "OIL_CHANGE",
                technicianId,
                serviceBayId,
                start,
                start.plusSeconds(3600),
                AppointmentStatus.CONFIRMED,
                start
        );
    }
}
