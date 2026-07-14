package com.keyloop.challenge.domain.service;

import com.keyloop.challenge.domain.entity.Appointment;
import com.keyloop.challenge.domain.valueobject.ResourceAssignment;
import com.keyloop.challenge.domain.valueobject.ServiceBayCandidate;
import com.keyloop.challenge.domain.valueobject.TechnicianCandidate;
import java.util.List;
import java.util.Optional;

public interface ResourceAssignmentPolicy {
    Optional<ResourceAssignment> select(
            List<ServiceBayCandidate> serviceBays,
            List<TechnicianCandidate> technicians,
            List<Appointment> overlappingAppointments
    );
}
