package com.keyloop.challenge.domain.service;

import com.keyloop.challenge.domain.entity.Appointment;
import com.keyloop.challenge.domain.valueobject.ResourceAssignment;
import com.keyloop.challenge.domain.valueobject.ServiceBayCandidate;
import com.keyloop.challenge.domain.valueobject.TechnicianCandidate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SkillFitPolicy implements ResourceAssignmentPolicy {
    @Override
    public Optional<ResourceAssignment> select(
            List<ServiceBayCandidate> serviceBays,
            List<TechnicianCandidate> technicians,
            List<Appointment> overlappingAppointments
    ) {
        Set<Long> occupiedBayIds = new HashSet<>();
        Set<Long> occupiedTechnicianIds = new HashSet<>();

        for (Appointment appointment : overlappingAppointments) {
            occupiedBayIds.add(appointment.getServiceBayId());
            occupiedTechnicianIds.add(appointment.getTechnicianId());
        }

        Long serviceBayId = serviceBays.stream()
                .filter(serviceBay -> !occupiedBayIds.contains(serviceBay.id()))
                .min(Comparator
                        .comparingLong(ServiceBayCandidate::appointmentCount)
                        .thenComparing(ServiceBayCandidate::id))
                .map(ServiceBayCandidate::id)
                .orElse(null);

        Long technicianId = technicians.stream()
                .filter(technician -> !occupiedTechnicianIds.contains(technician.id()))
                .min(Comparator
                        .comparingLong(TechnicianCandidate::skillCount)
                        .thenComparing(TechnicianCandidate::id))
                .map(TechnicianCandidate::id)
                .orElse(null);

        if (serviceBayId == null || technicianId == null) {
            return Optional.empty();
        }

        return Optional.of(new ResourceAssignment(serviceBayId, technicianId));
    }
}
