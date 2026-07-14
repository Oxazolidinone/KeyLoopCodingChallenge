package com.keyloop.challenge.application.service;

import com.keyloop.challenge.application.dto.AppointmentView;
import com.keyloop.challenge.application.dto.AvailabilityQuery;
import com.keyloop.challenge.application.dto.AvailabilityView;
import com.keyloop.challenge.application.dto.ScheduleAppointmentCommand;
import com.keyloop.challenge.application.mapper.AppointmentMapper;
import com.keyloop.challenge.domain.entity.Appointment;
import com.keyloop.challenge.domain.entity.AppointmentStatus;
import com.keyloop.challenge.domain.entity.ServiceType;
import com.keyloop.challenge.domain.entity.Vehicle;
import com.keyloop.challenge.domain.exception.BookingConflictException;
import com.keyloop.challenge.domain.exception.DomainNotFoundException;
import com.keyloop.challenge.domain.exception.InvalidBookingRequestException;
import com.keyloop.challenge.domain.repository.AppointmentRepository;
import com.keyloop.challenge.domain.repository.CustomerRepository;
import com.keyloop.challenge.domain.repository.DealershipRepository;
import com.keyloop.challenge.domain.repository.ServiceBayRepository;
import com.keyloop.challenge.domain.repository.ServiceTypeRepository;
import com.keyloop.challenge.domain.repository.TechnicianRepository;
import com.keyloop.challenge.domain.repository.VehicleRepository;
import com.keyloop.challenge.domain.service.ResourceAssignmentPolicy;
import com.keyloop.challenge.domain.valueobject.AvailabilityResult;
import com.keyloop.challenge.domain.valueobject.TimeWindow;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppointmentSchedulerService implements AppointmentService {
    private final CustomerRepository customers;
    private final VehicleRepository vehicles;
    private final DealershipRepository dealerships;
    private final ServiceTypeRepository serviceTypes;
    private final ServiceBayRepository serviceBays;
    private final TechnicianRepository technicians;
    private final AppointmentRepository appointments;
    private final ResourceAssignmentPolicy assignmentPolicy;
    private final AppointmentMapper mapper;
    private final Clock clock;

    @Override
    @Transactional
    public AppointmentView schedule(ScheduleAppointmentCommand command) {
        if (command.customerId() == null
                || command.vehicleId() == null
                || command.dealershipId() == null
                || command.serviceTypeCode() == null
                || command.requestedStart() == null) {
            throw new InvalidBookingRequestException(
                    "customerId, vehicleId, dealershipId, serviceTypeCode and requestedStart are required"
            );
        }

        if (!dealerships.existsById(command.dealershipId())) {
            throw new DomainNotFoundException("Dealership not found: " + command.dealershipId());
        }

        Vehicle vehicle = vehicles.findById(command.vehicleId())
                .orElseThrow(() -> new DomainNotFoundException("Vehicle not found: " + command.vehicleId()));
        if (!customers.existsById(command.customerId())) {
            throw new DomainNotFoundException("Customer not found: " + command.customerId());
        }
        if (!vehicle.getCustomerId().equals(command.customerId())) {
            throw new InvalidBookingRequestException("Vehicle does not belong to the customer");
        }

        ServiceType serviceType = serviceTypes.findByCode(
                command.serviceTypeCode().trim().toUpperCase(Locale.ROOT)
        )
                .orElseThrow(() -> new DomainNotFoundException("Service type not found: " + command.serviceTypeCode()));

        if (command.requestedStart().isBefore(Instant.now(clock))) {
            throw new InvalidBookingRequestException("Requested start time must be in the future");
        }

        TimeWindow slot = new TimeWindow(command.requestedStart(), command.requestedStart().plus(serviceType.duration()));

        serviceBays.findAllForUpdateByDealershipId(command.dealershipId());
        var serviceBayCandidates = serviceBays.findCandidatesByFit(
                command.dealershipId(),
                AppointmentStatus.CONFIRMED
        );
        var technicianCandidates = technicians.findQualifiedCandidates(
                command.dealershipId(),
                serviceType.getId()
        );

        var overlaps = appointments.findOverlappingAppointments(
                command.dealershipId(),
                slot.start(),
                slot.end(),
                AppointmentStatus.CONFIRMED
        );
        AvailabilityResult availability = assignmentPolicy.select(serviceBayCandidates, technicianCandidates, overlaps)
                .map(assignment -> AvailabilityResult.available(assignment, slot))
                .orElseGet(() -> AvailabilityResult.unavailable(slot));
        if (!availability.available()) {
            throw new BookingConflictException("No service bay and qualified technician are available for the requested time");
        }

        return mapper.toView(appointments.save(
                Appointment.confirmed(
                        command.customerId(),
                        command.vehicleId(),
                        command.dealershipId(),
                        serviceType,
                        availability.assignment(),
                        slot,
                        Instant.now(clock)
                )
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public AvailabilityView checkAvailability(AvailabilityQuery query) {
        if (query.dealershipId() == null || query.serviceTypeCode() == null || query.requestedStart() == null) {
            throw new InvalidBookingRequestException("dealershipId, serviceTypeCode and requestedStart are required");
        }
        if (!dealerships.existsById(query.dealershipId())) {
            throw new DomainNotFoundException("Dealership not found: " + query.dealershipId());
        }

        ServiceType serviceType = serviceTypes.findByCode(
                query.serviceTypeCode().trim().toUpperCase(Locale.ROOT)
        )
                .orElseThrow(() -> new DomainNotFoundException("Service type not found: " + query.serviceTypeCode()));

        if (query.requestedStart().isBefore(Instant.now(clock))) {
            throw new InvalidBookingRequestException("Requested start time must be in the future");
        }
        TimeWindow slot = new TimeWindow(query.requestedStart(), query.requestedStart().plus(serviceType.duration()));

        var serviceBayCandidates = serviceBays.findCandidatesByFit(
                query.dealershipId(),
                AppointmentStatus.CONFIRMED
        );
        var technicianCandidates = technicians.findQualifiedCandidates(
                query.dealershipId(),
                serviceType.getId()
        );
        var overlaps = appointments.findOverlappingAppointments(
                query.dealershipId(),
                slot.start(),
                slot.end(),
                AppointmentStatus.CONFIRMED
        );
        AvailabilityResult availability = assignmentPolicy.select(serviceBayCandidates, technicianCandidates, overlaps)
                .map(assignment -> AvailabilityResult.available(assignment, slot))
                .orElseGet(() -> AvailabilityResult.unavailable(slot));

        return mapper.toView(availability);
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentView getAppointment(UUID id) {
        return mapper.toView(appointments.findById(id)
                .orElseThrow(() -> new DomainNotFoundException("Appointment not found: " + id)));
    }
}
