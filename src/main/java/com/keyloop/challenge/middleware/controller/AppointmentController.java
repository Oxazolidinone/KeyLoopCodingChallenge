package com.keyloop.challenge.middleware.controller;

import com.keyloop.challenge.application.dto.AppointmentView;
import com.keyloop.challenge.application.dto.AvailabilityQuery;
import com.keyloop.challenge.application.dto.AvailabilityView;
import com.keyloop.challenge.application.dto.ScheduleAppointmentCommand;
import com.keyloop.challenge.application.service.AppointmentService;
import com.keyloop.challenge.middleware.dto.AppointmentResponse;
import com.keyloop.challenge.middleware.dto.AvailabilityResponse;
import com.keyloop.challenge.middleware.dto.ScheduleAppointmentRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
public class AppointmentController {
    private final AppointmentService scheduler;

    public AppointmentController(AppointmentService scheduler) {
        this.scheduler = scheduler;
    }

    @PostMapping("/appointments")
    public ResponseEntity<AppointmentResponse> schedule(@Valid @RequestBody ScheduleAppointmentRequest request) {
        AppointmentView appointment = scheduler.schedule(new ScheduleAppointmentCommand(
                request.customerId(),
                request.vehicleId(),
                request.dealershipId(),
                request.serviceTypeCode(),
                request.requestedStart().toInstant()
        ));

        return ResponseEntity
                .created(URI.create("/api/appointments/" + appointment.id()))
                .body(new AppointmentResponse(
                        appointment.id(),
                        appointment.customerId(),
                        appointment.vehicleId(),
                        appointment.dealershipId(),
                        appointment.serviceTypeCode(),
                        appointment.technicianId(),
                        appointment.serviceBayId(),
                        appointment.start(),
                        appointment.end(),
                        appointment.status()
                ));
    }

    @GetMapping("/appointments/{id}")
    public AppointmentResponse getAppointment(@PathVariable UUID id) {
        AppointmentView appointment = scheduler.getAppointment(id);

        return new AppointmentResponse(
                appointment.id(),
                appointment.customerId(),
                appointment.vehicleId(),
                appointment.dealershipId(),
                appointment.serviceTypeCode(),
                appointment.technicianId(),
                appointment.serviceBayId(),
                appointment.start(),
                appointment.end(),
                appointment.status()
        );
    }

    @GetMapping("/availability")
    public AvailabilityResponse checkAvailability(
            @RequestParam @NotNull Long dealershipId,
            @RequestParam @NotBlank String serviceTypeCode,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime requestedStart
    ) {
        AvailabilityView view = scheduler.checkAvailability(new AvailabilityQuery(
                dealershipId,
                serviceTypeCode,
                requestedStart.toInstant()
        ));

        return new AvailabilityResponse(
                view.available(),
                view.serviceBayId(),
                view.technicianId(),
                view.start(),
                view.end()
        );
    }
}
