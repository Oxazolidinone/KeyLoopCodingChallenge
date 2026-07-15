# Keyloop Coding Challenge

Backend implementation of **Scenario A: The Unified Service Scheduler**.

The application replaces manual service booking. It accepts a customer, vehicle, dealership, service type, and desired start time; checks a service bay and qualified technician for the complete service duration; and persists a confirmed appointment when both resources are available.

## Submission Documents

- [System Design Document](docs/SYSTEM_DESIGN.md)

## Implemented Capabilities

- Real-time availability check for a complete service duration.
- Vehicle ownership validation.
- Technician qualification filtering.
- Best-fit allocation that preserves multi-skilled technicians for harder work.
- Persistent confirmed appointments in PostgreSQL.
- Transactional protection against concurrent double-booking.
- Flyway-managed schema and deterministic sample data.
- Unit, service, controller, migration, and PostgreSQL concurrency tests.

## Architecture Summary

The project uses a pragmatic Onion Architecture:

~~~text
middleware -> application -> domain
                    ^
                    |
              infrastructure
             (composition root)
~~~

| Package | Responsibility |
| --- | --- |
| domain/entity | JPA entities and appointment status. |
| domain/valueobject | Immutable time windows, candidates, and resource assignments. |
| domain/service | Resource assignment strategy and best-fit implementation. |
| domain/repository | The single set of repositories extending JpaRepository. |
| application/dto | Commands, queries, and application views. |
| application/mapper | Domain-to-application response mapping. |
| application/service | Transactional use-case orchestration. |
| middleware | REST controllers, HTTP DTOs, and exception handling. |
| infrastructure | Spring Boot startup, dependency wiring, seed data, and migrations. |

Repository interfaces directly extend Spring Data JpaRepository to avoid duplicate domain ports, JPA repositories, and adapters in this assessment. This is a deliberate pragmatic trade-off: the domain ring is coupled to JPA/Spring Data. The full architecture and alternatives are documented in the [System Design Document](docs/SYSTEM_DESIGN.md).

Patterns used:

- Repository for persistence access.
- Strategy through ResourceAssignmentPolicy and SkillFitPolicy.
- Static Factory through Appointment.confirmed.
- Mapper between domain entities and application views.
- Transactional Application Service for booking orchestration.

## Prerequisites

- Java 17 or newer.
- Maven 3.9 or newer.
- Docker Desktop or another Docker-compatible runtime.
- cURL for the API walkthrough.

Confirm the tools are available:

~~~bash
java -version
mvn -version
docker version
~~~

## Quick Start

From the repository root:

~~~bash
docker compose up -d postgres
docker compose ps
mvn clean test
mvn spring-boot:run
~~~

If host port 5432 is already occupied, use the alternate-port commands in the [Configuration](#configuration) section.

The API starts at:

~~~text
http://localhost:8080
~~~

Flyway creates the schema on startup. Hibernate validates the mappings but does not create or update tables.

## Build and Test

Run the complete test suite:

~~~bash
mvn clean test
~~~

Docker must be running because the migration and concurrency tests use a real PostgreSQL container through Testcontainers.
The Compose PostgreSQL container is not required for `mvn test`; Testcontainers starts
an isolated PostgreSQL container on a random host port.

### Recommended Test Order

The tests are isolated and do not depend on execution order. `mvn clean test` is the
authoritative command. When diagnosing a failure or demonstrating the layers, run them
from the smallest scope to the largest scope:

| Order | Command | Verifies |
| --- | --- | --- |
| 1 | `mvn -Dtest=SkillFitPolicyTest test` | Pure best-fit allocation and overlap rules. |
| 2 | `mvn -Dtest=AppointmentSchedulerServiceTest test` | Booking, duration, fallback allocation, availability, and conflict behavior using the Spring application context. |
| 3 | `mvn -Dtest=AppointmentControllerTest test` | HTTP request mapping, 201 response, Location header, and response body. |
| 4 | `mvn -Dtest='PostgresMigrationTest#flywayCreatesSchemaAndApplicationCanUsePostgres' test` | Flyway and persistence against PostgreSQL 16. |
| 5 | `mvn -Dtest='PostgresMigrationTest#concurrentBookingsNeverReceiveTheSameResources' test` | Simultaneous transactions and pessimistic locking against PostgreSQL 16. |
| 6 | `mvn clean test` | All ten tests from a clean build. |

Steps 1 to 3 use the test configuration and H2 in PostgreSQL compatibility mode.
Steps 4 and 5 require a running Docker engine because they use Testcontainers.

The suite covers:

- best-fit resource selection;
- occupied-resource filtering;
- successful appointment creation;
- service duration calculation;
- fallback from a single-skill to a multi-skill technician;
- no-technician conflict behavior;
- HTTP request and response mapping;
- Flyway migration against PostgreSQL;
- simultaneous bookings against PostgreSQL.

Build the executable JAR:

~~~bash
mvn clean package
~~~

Run the packaged application while the Compose database is running:

~~~bash
java -jar target/keyloop-coding-challenge-1.0-SNAPSHOT.jar
~~~

## Configuration

The local defaults are:

| Setting | Default |
| --- | --- |
| JDBC URL | jdbc:postgresql://localhost:5432/keyloop |
| Database | keyloop |
| Username | keyloop |
| Password | keyloop |

Override them with environment variables:

~~~bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/keyloop
export SPRING_DATASOURCE_USERNAME=keyloop
export SPRING_DATASOURCE_PASSWORD=keyloop
mvn spring-boot:run
~~~

If port 5432 is already used by another local PostgreSQL instance, choose another host port for both Compose and the application:

~~~bash
POSTGRES_PORT=55432 docker compose up -d postgres
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/keyloop mvn spring-boot:run
~~~

## Seed Data

The application seeds:

| Type | Data |
| --- | --- |
| Customers | 1: Alex Nguyen, 2: Jordan Smith |
| Vehicles | 1 belongs to customer 1, 2 belongs to customer 2 |
| Dealership | 1: Central KeyLoop Dealership |
| Service types | OIL_CHANGE: 60 minutes, MOT: 90 minutes, BRAKE_INSPECTION: 120 minutes |
| Service bays | 1 and 2 |
| Technician 1 | OIL_CHANGE and MOT |
| Technician 2 | OIL_CHANGE and BRAKE_INSPECTION |
| Technician 3 | OIL_CHANGE only |

For OIL_CHANGE, technician 3 is preferred because that technician has only one qualification. This preserves technicians 1 and 2 for services with fewer qualified alternatives.

## API Test Harness

The following cURL walkthrough acts as the mocked client layer required by the assessment.

Set values used by the examples:

~~~bash
BASE_URL=http://localhost:8080
START_AT=2030-01-15T09:00:00Z
~~~

Use a different future start time when repeating the walkthrough against a database that already contains these appointments.

### 1. Check Availability

~~~bash
curl -sS --get "$BASE_URL/api/availability" \
  --data-urlencode "dealershipId=1" \
  --data-urlencode "serviceTypeCode=MOT" \
  --data-urlencode "requestedStart=$START_AT"
~~~

Expected shape:

~~~json
{
  "available": true,
  "serviceBayId": 1,
  "technicianId": 1,
  "start": "2030-01-15T09:00:00Z",
  "end": "2030-01-15T10:30:00Z"
}
~~~

### 2. Create an Appointment

~~~bash
curl -i -sS -X POST "$BASE_URL/api/appointments" \
  -H "Content-Type: application/json" \
  --data "{
    \"customerId\": 1,
    \"vehicleId\": 1,
    \"dealershipId\": 1,
    \"serviceTypeCode\": \"MOT\",
    \"requestedStart\": \"$START_AT\"
  }"
~~~

Expected result:

- HTTP 201 Created.
- A Location header containing /api/appointments/{id}.
- A CONFIRMED appointment containing technician 1 and a service bay.

### 3. Retrieve the Appointment

Copy the appointment ID from the create response:

~~~bash
curl -sS "$BASE_URL/api/appointments/<appointment-id>"
~~~

### 4. Verify the Conflict Rule

Only technician 1 is qualified for MOT. A second overlapping MOT request therefore returns 409 even though another bay exists:

~~~bash
curl -i -sS -X POST "$BASE_URL/api/appointments" \
  -H "Content-Type: application/json" \
  --data "{
    \"customerId\": 2,
    \"vehicleId\": 2,
    \"dealershipId\": 1,
    \"serviceTypeCode\": \"MOT\",
    \"requestedStart\": \"$START_AT\"
  }"
~~~

Expected error shape:

~~~json
{
  "timestamp": "2030-01-01T00:00:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "No service bay and qualified technician are available for the requested time"
}
~~~

The timestamp varies at runtime.

### Endpoint Summary

| Method | Endpoint | Function |
| --- | --- | --- |
| GET | /api/availability | Informational availability check. |
| POST | /api/appointments | Authoritative transactional check and creation. |
| GET | /api/appointments/{id} | Retrieve a confirmed appointment. |

| HTTP status | Meaning |
| --- | --- |
| 200 | Availability or appointment returned. |
| 201 | Appointment created. |
| 400 | Invalid request, past date, or vehicle/customer mismatch. |
| 404 | Requested domain record not found. |
| 409 | No valid bay and technician combination for the full duration. |

## Resource Selection

The SkillFitPolicy removes resources occupied by an overlapping confirmed appointment, then chooses:

1. the free service bay with the lowest historical confirmed appointment count;
2. the qualified free technician with the fewest qualifications;
3. the lowest ID as a deterministic tie-breaker.

Appointment overlap uses:

~~~text
existing.start < requested.end
AND existing.end > requested.start
~~~

This treats time windows as half-open intervals, so back-to-back appointments are valid.

## Concurrency

The availability endpoint is informational and does not reserve a resource.

POST /api/appointments runs the authoritative check and insert in one transaction. Immediately before checking resources, it obtains a PESSIMISTIC_WRITE lock on the dealership's service-bay rows in deterministic ID order. Because every appointment requires a bay, competing create requests for the same dealership cannot both confirm the same bay or technician.

This favors correctness and simplicity over maximum same-dealership write throughput. A production system with substantially higher contention should add PostgreSQL temporal exclusion constraints and bounded allocation retry. The trade-off is explained in the System Design Document.

### cURL Concurrency Demo

This is the visible concurrency demo. Run the following steps in order.

In terminal 1, start PostgreSQL and keep the application running:

~~~bash
docker compose up -d postgres
mvn spring-boot:run
~~~

In terminal 2, choose an unused future time and submit two overlapping MOT requests.
The trailing `&` starts each cURL process in the background; `wait` blocks until both
responses have arrived:

~~~bash
BASE_URL=http://localhost:8080
CONCURRENT_START=2030-01-16T09:00:00Z
RESULT_DIR="$(mktemp -d)"

# Remove only the fixed demo slot so this block can be run repeatedly.
docker exec keyloop-postgres psql -U keyloop -d keyloop -c \
  "delete from appointments
   where service_type_code = 'MOT'
     and start_time = '$CONCURRENT_START'::timestamptz;" > /dev/null

curl -sS -o "$RESULT_DIR/first.json" -w "%{http_code}\n" \
  -X POST "$BASE_URL/api/appointments" \
  -H "Content-Type: application/json" \
  --data "{
    \"customerId\": 1,
    \"vehicleId\": 1,
    \"dealershipId\": 1,
    \"serviceTypeCode\": \"MOT\",
    \"requestedStart\": \"$CONCURRENT_START\"
  }" > "$RESULT_DIR/first.status" &
FIRST_PID=$!

curl -sS -o "$RESULT_DIR/second.json" -w "%{http_code}\n" \
  -X POST "$BASE_URL/api/appointments" \
  -H "Content-Type: application/json" \
  --data "{
    \"customerId\": 2,
    \"vehicleId\": 2,
    \"dealershipId\": 1,
    \"serviceTypeCode\": \"MOT\",
    \"requestedStart\": \"$CONCURRENT_START\"
  }" > "$RESULT_DIR/second.status" &
SECOND_PID=$!

wait "$FIRST_PID"
wait "$SECOND_PID"

printf "Request 1: HTTP %s\n" "$(cat "$RESULT_DIR/first.status")"
printf "Request 2: HTTP %s\n" "$(cat "$RESULT_DIR/second.status")"
printf "Request 1 body:\n"
cat "$RESULT_DIR/first.json"
printf "\nRequest 2 body:\n"
cat "$RESULT_DIR/second.json"
printf "\n"
~~~

Only technician 1 is qualified for MOT. The expected status set is one `201` and one
`409`; which request wins is intentionally nondeterministic. The first transaction to
obtain the lock creates the appointment. The other transaction waits, checks again
after the lock is released, and returns `409` instead of double-booking technician 1.

Still in terminal 2, confirm that PostgreSQL contains exactly one appointment for the
contested slot:

~~~bash
docker exec keyloop-postgres psql -U keyloop -d keyloop -c \
  "select id, customer_id, technician_id, service_bay_id, status
   from appointments
   where service_type_code = 'MOT'
     and start_time = '$CONCURRENT_START'::timestamptz;"
~~~

The expected query result is one row using technician 1. The first command in the cURL
block removes only this fixed demo slot, making the demo repeatable without resetting
the rest of the database.

The cURL test order is therefore:

1. Start PostgreSQL.
2. Start the application and wait for `Started KeyloopApplication`.
3. Fire both background cURL requests from the same terminal block.
4. Verify that the status codes are exactly `201` and `409`, in either order.
5. Query PostgreSQL and verify that the contested slot contains exactly one row.

### Automated Concurrency Test

For a deterministic repeatable test, run:

~~~bash
mvn -Dtest='PostgresMigrationTest#concurrentBookingsNeverReceiveTheSameResources' test
~~~

This test uses latches to release two workers together. Both workers request
`OIL_CHANGE`, for which enough resources exist, so both bookings must succeed with
different `serviceBayId` and `technicianId` values. The cURL demo above is the visual
proof for the API; this test is the automated regression check.

## AI Collaboration Narrative

### Strategy

I used Codex as an iterative engineering collaborator. I did not ask it for a one-shot solution and accept the result. I first supplied the business scenario, then directed separate passes for domain analysis, endpoint design, concurrency risks, architecture, implementation, migrations, testing, and final review.

My role was to define the constraints, question design choices, select trade-offs, and verify that generated changes matched both the assessment and the intended architecture.

### Guidance and Refinement

Several generated or proposed directions were changed after review:

- I rejected a domain-port, JPA-repository, and adapter combination because it created three persistence abstractions for a small application.
- I required entities to remain in the domain instead of infrastructure.
- I removed rate limiting from the core scope because booking correctness was the higher-value risk.
- I rejected first-available technician selection and required a best-fit strategy that preserves multi-skilled technicians.
- I challenged dealership-row locking and moved synchronization to the constrained service-bay resources.
- I required application service implementations to contain only interface methods, moving mapping and assignment behavior to focused collaborators.

### Verification Process

I verified and refined AI output through:

- source and package dependency inspection;
- clean Maven compilation rather than relying on generated code appearance;
- domain unit tests for allocation rules;
- service and controller tests for use-case and HTTP behavior;
- Flyway migration validation;
- Testcontainers against PostgreSQL rather than relying only on H2;
- a simultaneous-booking test for double-booking behavior;
- final searches for stale package names, reverse layer dependencies, and unused architecture remnants;
- a complete mvn clean test run.

The current clean suite contains 10 passing tests with no failures or errors.

### Ownership

AI accelerated boilerplate, alternatives, refactoring, and test generation, but each material decision was reviewed against the business rules. The final code, documented assumptions, pragmatic Onion trade-off, locking strategy, and known limitations are choices I understand and take responsibility for.

Additional detail about GenAI use during the design phase is available in the [System Design Document](docs/SYSTEM_DESIGN.md#16-genai-use-during-design).

## Known Limitations

- No authentication or dealership-scoped authorization.
- No cancellation or rescheduling.
- No opening-hours, shift, leave, or bay-maintenance calendar.
- No idempotency key for safe client retries.
- Same-dealership create operations are serialized by the current locking strategy.
- Observability is documented as a production strategy but custom metrics, tracing, and Actuator endpoints are not implemented.

These limitations are outside the core Scenario A acceptance criteria and are prioritized in the System Design Document.

## Stop or Reset the Environment

Stop the application with Ctrl+C, then stop PostgreSQL:

~~~bash
docker compose down
~~~

To delete all local challenge database data and rerun Flyway from an empty database:

~~~bash
docker compose down -v
~~~

The second command permanently removes the local Docker volume.
