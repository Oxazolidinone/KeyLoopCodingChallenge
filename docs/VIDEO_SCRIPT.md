# Video Script: Scenario A

Target duration: 8 to 9 minutes.

The spoken script is in English. Text under **On screen** is an action cue and should not be read aloud.

Replace **[YOUR NAME]** before recording.

## Before Recording

1. Close unrelated applications and hide notifications.
2. Use a readable editor and terminal font size.
3. Open these files in advance:
   - docs/SYSTEM_DESIGN.md
   - src/main/java/com/keyloop/challenge/application/service/AppointmentSchedulerService.java
   - src/main/java/com/keyloop/challenge/domain/service/SkillFitPolicy.java
   - src/main/java/com/keyloop/challenge/domain/repository/ServiceBayRepository.java
   - src/test/java/com/keyloop/challenge/infrastructure/migration/PostgresMigrationTest.java
4. Reset the local demo database if the selected time has already been used:

~~~bash
docker compose down -v
docker compose up -d postgres
~~~

If another local database already uses port 5432, use these commands instead of the default database and application start commands:

~~~bash
POSTGRES_PORT=55432 docker compose up -d postgres
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/keyloop mvn spring-boot:run
~~~

5. Start the application before the recording:

~~~bash
mvn spring-boot:run
~~~

6. In a second terminal, prepare:

~~~bash
BASE_URL=http://localhost:8080
START_AT=2030-01-15T09:00:00Z
~~~

7. Keep the cURL commands from the README ready to paste.

## Run of Show

| Time | Section |
| --- | --- |
| 0:00-0:35 | Introduction |
| 0:35-1:25 | Problem and assumptions |
| 1:25-2:40 | Architecture |
| 2:40-3:55 | Booking flow and concurrency |
| 3:55-5:05 | Implementation highlights |
| 5:05-6:30 | AI collaboration story |
| 6:30-7:55 | Application demonstration |
| 7:55-8:40 | Tests, challenges, and learning |
| 8:40-8:55 | Closing |

## 0:00-0:35 - Introduction

**On screen:** Show the repository README title.

**Say:**

Hello, my name is [YOUR NAME]. For the Keyloop technical assessment, I selected Scenario A, the Unified Service Scheduler, in the Ownership domain.

I implemented the backend service using Java, Spring Boot, PostgreSQL, and Maven. In this presentation, I will briefly explain the design, highlight the most important implementation decisions, describe how I collaborated with AI, and demonstrate the API.

## 0:35-1:25 - Problem and Assumptions

**On screen:** Show the goals and assumptions sections in docs/SYSTEM_DESIGN.md.

**Say:**

The core problem is not only creating an appointment record. Before confirmation, the system must prove that a service bay and a qualified technician are both free for the complete service duration.

I made several assumptions explicit. Every appointment uses exactly one bay and one technician. Each service type has a fixed duration. Timestamps are handled in UTC, and time ranges are half-open, so one appointment may start exactly when another one ends.

I implemented the backend as the selected service layer. The client side is represented by a cURL test harness. Features such as authentication, rescheduling, opening hours, and technician shift management are documented as future work because they are outside the three core acceptance criteria.

## 1:25-2:40 - Architecture

**On screen:** Show the component diagram in docs/SYSTEM_DESIGN.md.

**Say:**

The application follows a pragmatic Onion Architecture.

The middleware ring owns REST controllers, HTTP request and response models, and exception-to-HTTP mapping. The application ring owns commands, queries, response views, mapping, and transactional use-case orchestration. The domain core contains entities, value objects, repository interfaces, and the resource assignment strategy. Infrastructure starts Spring Boot, wires dependencies, runs Flyway migrations, and seeds demonstration data.

The main dependency direction is from middleware to application to domain. The application does not import infrastructure.

I deliberately use one set of repository interfaces that directly extend Spring Data JpaRepository. This avoids separate domain ports, JPA repositories, and adapter classes for every entity. I understand that this couples the domain ring to JPA and Spring Data, so I documented it as a pragmatic trade-off rather than claiming a completely framework-independent domain.

PostgreSQL is the persistent store, and Flyway owns the schema. Hibernate validates the mapping but does not generate tables.

## 2:40-3:55 - Booking Flow and Concurrency

**On screen:** Show the booking sequence diagram, then ServiceBayRepository.findAllForUpdateByDealershipId.

**Say:**

For a booking request, the application first validates the dealership, customer, vehicle ownership, service type, and requested time. It calculates the end time from the service duration.

Immediately before the authoritative availability check, the transaction obtains a pessimistic write lock on the dealership's service-bay rows in deterministic ID order. It then loads bay candidates, qualified technician candidates, and all confirmed appointments that overlap the requested window.

The assignment strategy removes occupied resources. It selects the least-loaded free bay and prefers the qualified technician with the fewest qualifications. This preserves multi-skilled technicians for jobs that have fewer alternatives.

The lock is important because two requests could otherwise read the same resources as free before either insert commits. Since every appointment requires a bay, requests for the same dealership are serialized across the check and insert. This favors simple correctness over maximum write throughput.

For higher scale, my next step would be PostgreSQL temporal exclusion constraints with bounded allocation retry. The public availability endpoint remains informational; the create endpoint always checks again inside its transaction.

## 3:55-5:05 - Implementation Highlights

**On screen:** Show AppointmentSchedulerService, Appointment.confirmed, and SkillFitPolicy.

**Say:**

The AppointmentSchedulerService is the transactional application service. Its implementation contains only the three operations defined by the AppointmentService interface: schedule, check availability, and get appointment.

HTTP mapping is outside the service, and resource-selection logic is outside the service. SkillFitPolicy implements the Strategy pattern and can be replaced without changing the booking orchestration.

Appointment creation uses a named static factory, Appointment.confirmed, so the confirmed status, generated ID, selected resources, service details, time window, and creation timestamp are created together.

The repository queries use projections for bay workload and technician skill count. The overlap condition checks that an existing start is before the requested end and the existing end is after the requested start. This verifies availability for the entire duration and still permits back-to-back appointments.

The schema includes foreign keys for every appointment association and indexes for dealership, service bay, technician, and time-based access.

## 5:05-6:30 - AI Collaboration Story

**On screen:** Show the AI Collaboration Narrative in the README.

**Say:**

I used Codex as an iterative engineering collaborator, but I did not accept a one-shot generated solution.

I first provided the full scenario and asked the agent to identify entities, endpoints, ambiguous requirements, race conditions, and scaling risks. I then constrained the architecture and reviewed each major result.

Several initial directions were changed. I rejected three persistence layers made from domain repository ports, JPA repositories, and adapters because that was unnecessary for this assessment. I required entities to remain in the domain. I removed rate limiting from the core scope because resource correctness was more important.

I also rejected first-available technician selection. I directed the agent to implement a best-fit strategy that reserves multi-skilled technicians for harder services. I challenged locking the dealership record and moved synchronization to service-bay resources.

Most importantly, I verified the output. I inspected package dependencies and repository queries, ran clean Maven builds, added focused domain and service tests, validated the migration with PostgreSQL Testcontainers, and added a simultaneous-booking test. When generated code compiled but did not match my architectural intent, I asked for it to be refactored.

AI accelerated exploration, boilerplate, and testing, but the design choices, trade-offs, and final quality remain my responsibility.

## 6:30-7:55 - Application Demonstration

### Availability

**On screen:** Run the availability cURL command from the README.

**Say:**

I will request availability for an MOT service at dealership one. The response is available and returns service bay one and technician one. The end time is ninety minutes after the requested start because that duration comes from the MOT service type.

### Successful Booking

**On screen:** Run the first POST command. Point to HTTP 201, the Location header, status, technician ID, and bay ID.

**Say:**

Now I create the appointment for customer one and vehicle one. The API returns HTTP 201 Created, a Location header, and a confirmed persistent appointment containing the selected technician and service bay.

The customer and vehicle relationship is validated before resources are allocated.

### Retrieve

**On screen:** Copy the appointment ID and call GET /api/appointments/{id}.

**Say:**

Using the returned identifier, I can retrieve the same confirmed appointment from PostgreSQL.

### Conflict

**On screen:** Run the second POST for customer two at the same MOT start time.

**Say:**

Finally, I submit another MOT booking for the same window. There is still another physical bay, but only technician one is qualified for MOT and that technician is already occupied. The API correctly returns HTTP 409 Conflict instead of creating an invalid appointment.

## 7:55-8:40 - Tests, Challenges, and Learning

**On screen:** Show PostgresMigrationTest, then a terminal with the result of mvn clean test.

**Say:**

The clean test suite contains ten passing tests. It covers domain allocation, service behavior, HTTP mapping, Flyway migration, real PostgreSQL persistence, and concurrent requests.

The hardest part was the boundary between an availability read and a booking guarantee. A response can become stale immediately, so correctness has to come from the transaction that performs the final check and insert.

I also learned that AI-generated code can be functionally plausible while still violating the intended architecture or using a weak allocation rule. Precise constraints, review, and executable verification were more valuable than simply generating more code.

## 8:40-8:55 - Closing

**On screen:** Return to the README or architecture diagram.

**Say:**

This solution fulfills the three Scenario A requirements and documents the deliberate limitations and next production steps. Thank you for reviewing my Keyloop technical assessment.

## Optional Shortening

If the recording exceeds ten minutes:

- Remove the detailed list of non-goals from the problem section.
- Shorten the implementation section to the Strategy and Static Factory paragraphs.
- Keep the AI collaboration section between one and two minutes because it is an explicit evaluation criterion.
- Do not remove the successful booking and conflict demonstrations.
