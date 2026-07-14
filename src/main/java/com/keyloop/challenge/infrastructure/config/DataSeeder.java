package com.keyloop.challenge.infrastructure.config;

import com.keyloop.challenge.domain.entity.Customer;
import com.keyloop.challenge.domain.entity.Dealership;
import com.keyloop.challenge.domain.entity.ServiceBay;
import com.keyloop.challenge.domain.entity.ServiceType;
import com.keyloop.challenge.domain.entity.Technician;
import com.keyloop.challenge.domain.entity.TechnicianQualification;
import com.keyloop.challenge.domain.entity.Vehicle;
import com.keyloop.challenge.domain.repository.CustomerRepository;
import com.keyloop.challenge.domain.repository.DealershipRepository;
import com.keyloop.challenge.domain.repository.ServiceBayRepository;
import com.keyloop.challenge.domain.repository.ServiceTypeRepository;
import com.keyloop.challenge.domain.repository.TechnicianQualificationRepository;
import com.keyloop.challenge.domain.repository.TechnicianRepository;
import com.keyloop.challenge.domain.repository.VehicleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

@Configuration
public class DataSeeder {
    @Bean
    CommandLineRunner seedReferenceData(ReferenceDataSeed seed) {
        return args -> seed.run();
    }

    @Configuration
    static class ReferenceDataSeed {
        private final CustomerRepository customers;
        private final VehicleRepository vehicles;
        private final DealershipRepository dealerships;
        private final ServiceTypeRepository serviceTypes;
        private final ServiceBayRepository serviceBays;
        private final TechnicianRepository technicians;
        private final TechnicianQualificationRepository qualifications;

        ReferenceDataSeed(
                CustomerRepository customers,
                VehicleRepository vehicles,
                DealershipRepository dealerships,
                ServiceTypeRepository serviceTypes,
                ServiceBayRepository serviceBays,
                TechnicianRepository technicians,
                TechnicianQualificationRepository qualifications
        ) {
            this.customers = customers;
            this.vehicles = vehicles;
            this.dealerships = dealerships;
            this.serviceTypes = serviceTypes;
            this.serviceBays = serviceBays;
            this.technicians = technicians;
            this.qualifications = qualifications;
        }

        @Transactional
        void run() {
            if (customers.count() > 0) {
                return;
            }

            customers.save(new Customer(1L, "Alex Nguyen"));
            customers.save(new Customer(2L, "Jordan Smith"));

            vehicles.save(new Vehicle(1L, 1L, "KEY-100"));
            vehicles.save(new Vehicle(2L, 2L, "KEY-200"));

            dealerships.save(new Dealership(1L, "Central KeyLoop Dealership"));

            serviceTypes.save(new ServiceType(1L, "OIL_CHANGE", "Oil Change", 60));
            serviceTypes.save(new ServiceType(2L, "MOT", "MOT Test", 90));
            serviceTypes.save(new ServiceType(3L, "BRAKE_INSPECTION", "Brake Inspection", 120));

            serviceBays.save(new ServiceBay(1L, 1L, "Bay 1"));
            serviceBays.save(new ServiceBay(2L, 1L, "Bay 2"));

            technicians.save(new Technician(1L, 1L, "Alice Technician"));
            technicians.save(new Technician(2L, 1L, "Bob Technician"));
            technicians.save(new Technician(3L, 1L, "Casey Technician"));

            qualifications.save(new TechnicianQualification(1L, 1L));
            qualifications.save(new TechnicianQualification(1L, 2L));
            qualifications.save(new TechnicianQualification(2L, 1L));
            qualifications.save(new TechnicianQualification(2L, 3L));
            qualifications.save(new TechnicianQualification(3L, 1L));
        }
    }
}
