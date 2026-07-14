package com.keyloop.challenge.application.service;

import com.keyloop.challenge.application.dto.AppointmentView;
import com.keyloop.challenge.application.dto.AvailabilityQuery;
import com.keyloop.challenge.application.dto.AvailabilityView;
import com.keyloop.challenge.application.dto.ScheduleAppointmentCommand;
import java.util.UUID;

public interface AppointmentService {
    AppointmentView schedule(ScheduleAppointmentCommand command);

    AvailabilityView checkAvailability(AvailabilityQuery query);

    AppointmentView getAppointment(UUID id);
}
