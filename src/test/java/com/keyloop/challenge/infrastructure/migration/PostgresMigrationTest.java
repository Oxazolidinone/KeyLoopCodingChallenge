package com.keyloop.challenge.infrastructure.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.keyloop.challenge.application.dto.AppointmentView;
import com.keyloop.challenge.application.dto.ScheduleAppointmentCommand;
import com.keyloop.challenge.application.service.AppointmentService;
import com.keyloop.challenge.infrastructure.boot.Main;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = Main.class)
class PostgresMigrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("keyloop")
            .withUsername("keyloop")
            .withPassword("keyloop");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppointmentService scheduler;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Test
    void flywayCreatesSchemaAndApplicationCanUsePostgres() {
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true",
                Integer.class
        );
        Integer appointmentColumns = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.columns
                        where table_name = 'appointments'
                          and column_name in ('id', 'customer_id', 'vehicle_id', 'technician_id', 'service_bay_id')
                        """,
                Integer.class
        );
        Integer seededTechnicians = jdbcTemplate.queryForObject("select count(*) from technicians", Integer.class);

        assertEquals(1, successfulMigrations);
        assertEquals(5, appointmentColumns);
        assertEquals(3, seededTechnicians);

        Instant start = Instant.now().plus(14, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        AppointmentView appointment = scheduler.schedule(new ScheduleAppointmentCommand(
                1L,
                1L,
                1L,
                "OIL_CHANGE",
                start
        ));

        assertNotNull(appointment.id());
        assertEquals(3L, appointment.technicianId());
    }

    @Test
    @Timeout(20)
    void concurrentBookingsNeverReceiveTheSameResources() throws Exception {
        Instant start = Instant.now().plus(15, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch startTogether = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<AppointmentView> first = executor.submit(() -> {
                ready.countDown();
                startTogether.await();
                return scheduler.schedule(new ScheduleAppointmentCommand(1L, 1L, 1L, "OIL_CHANGE", start));
            });
            Future<AppointmentView> second = executor.submit(() -> {
                ready.countDown();
                startTogether.await();
                return scheduler.schedule(new ScheduleAppointmentCommand(2L, 2L, 1L, "OIL_CHANGE", start));
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            startTogether.countDown();

            AppointmentView firstAppointment = first.get(10, TimeUnit.SECONDS);
            AppointmentView secondAppointment = second.get(10, TimeUnit.SECONDS);
            assertNotEquals(firstAppointment.serviceBayId(), secondAppointment.serviceBayId());
            assertNotEquals(firstAppointment.technicianId(), secondAppointment.technicianId());
        } finally {
            startTogether.countDown();
            executor.shutdownNow();
        }
    }
}
