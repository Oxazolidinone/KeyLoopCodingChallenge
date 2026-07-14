package com.keyloop.challenge.application.mapper;

import com.keyloop.challenge.application.dto.AppointmentView;
import com.keyloop.challenge.application.dto.AvailabilityView;
import com.keyloop.challenge.domain.entity.Appointment;
import com.keyloop.challenge.domain.valueobject.AvailabilityResult;
import org.springframework.stereotype.Component;

@Component
public class AppointmentMapper {
    public AppointmentView toView(Appointment appointment) {
        return new AppointmentView(
                appointment.getId(),
                appointment.getCustomerId(),
                appointment.getVehicleId(),
                appointment.getDealershipId(),
                appointment.getServiceTypeCode(),
                appointment.getTechnicianId(),
                appointment.getServiceBayId(),
                appointment.getStartTime(),
                appointment.getEndTime(),
                appointment.getStatus()
        );
    }

    public AvailabilityView toView(AvailabilityResult result) {
        Long serviceBayId = result.assignment() == null ? null : result.assignment().serviceBayId();
        Long technicianId = result.assignment() == null ? null : result.assignment().technicianId();
        return new AvailabilityView(
                result.available(),
                serviceBayId,
                technicianId,
                result.slot().start(),
                result.slot().end()
        );
    }
}
