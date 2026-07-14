package com.keyloop.challenge.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.keyloop.challenge.application.dto.AppointmentView;
import com.keyloop.challenge.application.dto.AvailabilityQuery;
import com.keyloop.challenge.application.dto.AvailabilityView;
import com.keyloop.challenge.application.dto.ScheduleAppointmentCommand;
import com.keyloop.challenge.domain.entity.AppointmentStatus;
import com.keyloop.challenge.domain.exception.BookingConflictException;
import com.keyloop.challenge.infrastructure.boot.Main;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = Main.class)
@Transactional
class AppointmentSchedulerServiceTest {
    @Autowired
    private AppointmentService scheduler;

    @Test
    void schedulesConfirmedAppointmentWithServiceBayAndQualifiedTechnician() {
        Instant start = futureSlot(1);

        AppointmentView appointment = scheduler.schedule(new ScheduleAppointmentCommand(
                1L,
                1L,
                1L,
                "OIL_CHANGE",
                start
        ));

        assertNotNull(appointment.id());
        assertEquals(AppointmentStatus.CONFIRMED, appointment.status());
        assertEquals(1L, appointment.serviceBayId());
        assertEquals(3L, appointment.technicianId());
        assertEquals(start.plus(60, ChronoUnit.MINUTES), appointment.end());
        assertEquals(appointment, scheduler.getAppointment(appointment.id()));
    }

    @Test
    void fallsBackToMultiSkillTechnicianWhenSingleSkillTechnicianIsBusy() {
        Instant start = futureSlot(4);
        scheduler.schedule(new ScheduleAppointmentCommand(1L, 1L, 1L, "OIL_CHANGE", start));

        AppointmentView appointment = scheduler.schedule(new ScheduleAppointmentCommand(
                2L,
                2L,
                1L,
                "OIL_CHANGE",
                start
        ));

        assertEquals(2L, appointment.serviceBayId());
        assertEquals(1L, appointment.technicianId());
    }

    @Test
    void rejectsOverlappingBookingWhenNoQualifiedTechnicianIsAvailableForWholeDuration() {
        Instant start = futureSlot(2);
        scheduler.schedule(new ScheduleAppointmentCommand(1L, 1L, 1L, "MOT", start));

        BookingConflictException exception = assertThrows(
                BookingConflictException.class,
                () -> scheduler.schedule(new ScheduleAppointmentCommand(2L, 2L, 1L, "MOT", start))
        );

        assertEquals(
                "No service bay and qualified technician are available for the requested time",
                exception.getMessage()
        );
    }

    @Test
    void availabilityIsFalseWhenAQualifiedTechnicianIsAlreadyBooked() {
        Instant start = futureSlot(3);
        scheduler.schedule(new ScheduleAppointmentCommand(1L, 1L, 1L, "MOT", start));

        AvailabilityView availability = scheduler.checkAvailability(new AvailabilityQuery(1L, "MOT", start));

        assertFalse(availability.available());
        assertEquals(start, availability.start());
        assertEquals(start.plus(90, ChronoUnit.MINUTES), availability.end());
    }

    private Instant futureSlot(int dayOffset) {
        return Instant.now()
                .plus(dayOffset, ChronoUnit.DAYS)
                .truncatedTo(ChronoUnit.MINUTES);
    }
}
