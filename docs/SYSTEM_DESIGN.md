# System Design: Unified Service Scheduler

## 1. Executive Summary

This document describes the backend design for Keyloop Technical Assessment Scenario A: The Unified Service Scheduler.

The system accepts a requested dealership, customer, vehicle, service type, and start time. Before confirming the appointment, it checks that:

- the vehicle belongs to the customer;
- a service bay is available for the complete service duration;
- a technician is available for the complete duration;
- the technician is qualified for the requested service type.

When all conditions are met, the system persists a confirmed appointment containing the selected customer, vehicle, dealership, service type, technician, and service bay.

The submitted implementation is a Spring Boot REST API backed by PostgreSQL. A frontend is outside the selected implementation scope and is represented by documented cURL requests.

## 2. Goals and Non-Goals

### Goals

- Provide a REST API for availability checks and appointment creation.
- Prevent a service bay or technician from being double-booked.
- Persist confirmed appointments in PostgreSQL.
- Prefer the best-fit technician instead of simply choosing the first result.
- Keep business policy separate from HTTP and database configuration.
- Make schema changes reproducible through versioned migrations.
- Validate the core rules with unit, API, migration, and concurrency tests.

### Non-Goals for This Assessment

- Customer, vehicle, technician, service bay, or service type administration.
- Authentication, authorization, and dealership tenant management.
- Appointment cancellation or rescheduling.
- Dealership opening hours, holidays, technician shifts, or absence management.
- Multi-service work orders that require more than one technician or bay.
- Notifications, payments, or integration with external dealer systems.
- A production user interface.

## 3. Assumptions

The original scenario intentionally leaves several details open. The implementation uses these documented assumptions:

1. Every appointment requires exactly one service bay and one technician.
2. Each service type has a fixed duration in minutes.
3. A technician is qualified through an explicit technician-to-service-type record.
4. All date-time values are stored and compared as UTC instants.
5. Appointment windows are half-open intervals: [start, end). Therefore, an appointment ending at 10:00 does not conflict with another starting at 10:00.
6. The availability endpoint is advisory. The create endpoint always performs the authoritative check again inside its transaction.
7. Reference data is seeded for demonstration and treated as stable while appointments are being booked.
8. The sample dataset contains one dealership, but dealership identity remains part of the model and API because it is explicitly required by the scenario and preserves a future multi-dealership boundary.
9. Only confirmed appointments consume resources. Cancellation states can be added later without changing the overlap rule.
10. The backend is the fully implemented service layer. cURL commands in the README act as the client-side test harness.

## 4. Architecture

### 4.1 Component Diagram

~~~mermaid
flowchart LR
    Client["API client<br/>cURL / future UI"]

    subgraph Middleware["Middleware ring"]
        Controller["AppointmentController"]
        ErrorHandler["GlobalExceptionHandler"]
        HttpModels["Request / Response DTOs"]
    end

    subgraph Application["Application ring"]
        AppService["AppointmentService<br/>AppointmentSchedulerService"]
        AppModels["Commands / Queries / Views"]
        Mapper["AppointmentMapper"]
    end

    subgraph Domain["Domain core"]
        Entities["Entities and Value Objects"]
        Policy["ResourceAssignmentPolicy<br/>SkillFitPolicy"]
        Repositories["Spring Data Repository Interfaces"]
    end

    subgraph Infrastructure["Infrastructure and composition"]
        Boot["Spring Boot Main"]
        Config["Bean configuration and seed data"]
        Flyway["Flyway migrations"]
        JpaRuntime["Spring Data JPA / Hibernate"]
    end

    Database[("PostgreSQL")]

    Client --> Controller
    Controller --> HttpModels
    Controller --> AppService
    AppService --> AppModels
    AppService --> Entities
    AppService --> Policy
    AppService --> Repositories
    AppService --> Mapper
    Repositories -. "runtime implementation" .-> JpaRuntime
    JpaRuntime --> Database
    Flyway --> Database
    Boot -. "starts and scans" .-> Middleware
    Boot -. "starts and scans" .-> Application
    Config -. "wires policy and clock" .-> AppService
    ErrorHandler -. "maps exceptions to HTTP" .-> Client
~~~

### 4.2 Dependency Direction

The source packages follow a pragmatic Onion Architecture:

- **Domain** owns entities, immutable value objects, the assignment strategy, and repository contracts.
- **Application** orchestrates use cases and transactions. It depends on the domain but not on middleware or infrastructure.
- **Middleware** translates HTTP requests into application commands and application views into HTTP responses.
- **Infrastructure** is the composition root. It starts Spring, configures dependencies, runs migrations, and seeds sample data.

The main dependency direction is:

**middleware -> application -> domain**

Infrastructure wires these rings together.

This is intentionally a pragmatic rather than framework-pure Onion design. Repository interfaces directly extend Spring Data JpaRepository, and domain entities contain JPA annotations. This avoids three versions of each repository (domain port, JPA repository, and adapter) in a small assessment. The trade-off is that the domain module has compile-time dependencies on JPA and Spring Data. A strict framework-independent domain would restore ports and adapters if that isolation became valuable.

## 5. Component Responsibilities

| Component | Responsibility |
| --- | --- |
| AppointmentController | Exposes REST endpoints and translates HTTP models to application commands and queries. |
| GlobalExceptionHandler | Maps validation, not-found, and booking conflict exceptions to consistent HTTP responses. |
| AppointmentService | Defines the application operations available to the middleware. |
| AppointmentSchedulerService | Validates the request, controls the transaction, obtains the concurrency lock, loads candidates, applies the assignment policy, and persists the appointment. |
| AppointmentMapper | Prevents JPA entities from becoming the public API contract. |
| Appointment | Represents the persisted booking and provides the confirmed appointment factory method. |
| TimeWindow | Encapsulates a valid start/end interval and its overlap semantics. |
| ResourceAssignmentPolicy | Strategy contract for selecting a service bay and technician. |
| SkillFitPolicy | Selects a free, least-loaded bay and the qualified technician with the fewest skills. |
| Repositories | Provide entity persistence, candidate projections, overlap queries, and pessimistic resource locking. |
| Flyway migration | Creates the schema, constraints, foreign keys, and time lookup indexes. |
| DataSeeder | Loads deterministic reference data for local execution and demonstrations. |
| PostgreSQL | Stores reference data and confirmed appointments and supplies transactional row locking. |

## 6. Data Model

~~~mermaid
erDiagram
    CUSTOMER ||--o{ VEHICLE : owns
    CUSTOMER ||--o{ APPOINTMENT : books
    VEHICLE ||--o{ APPOINTMENT : receives
    DEALERSHIP ||--o{ SERVICE_BAY : contains
    DEALERSHIP ||--o{ TECHNICIAN : employs
    DEALERSHIP ||--o{ APPOINTMENT : hosts
    SERVICE_TYPE ||--o{ TECHNICIAN_QUALIFICATION : requires
    TECHNICIAN ||--o{ TECHNICIAN_QUALIFICATION : has
    SERVICE_TYPE ||--o{ APPOINTMENT : defines
    TECHNICIAN ||--o{ APPOINTMENT : performs
    SERVICE_BAY ||--o{ APPOINTMENT : hosts

    CUSTOMER {
        bigint id PK
        varchar name
    }
    VEHICLE {
        bigint id PK
        bigint customer_id FK
        varchar registration_number UK
    }
    DEALERSHIP {
        bigint id PK
        varchar name
    }
    SERVICE_TYPE {
        bigint id PK
        varchar code UK
        bigint duration_minutes
    }
    SERVICE_BAY {
        bigint id PK
        bigint dealership_id FK
        varchar name
    }
    TECHNICIAN {
        bigint id PK
        bigint dealership_id FK
        varchar name
    }
    TECHNICIAN_QUALIFICATION {
        bigint id PK
        bigint technician_id FK
        bigint service_type_id FK
    }
    APPOINTMENT {
        uuid id PK
        bigint customer_id FK
        bigint vehicle_id FK
        bigint dealership_id FK
        bigint service_type_id FK
        bigint technician_id FK
        bigint service_bay_id FK
        timestamptz start_time
        timestamptz end_time
        varchar status
        timestamptz created_at
    }
~~~

Foreign keys protect all persisted associations. A unique constraint prevents duplicate technician qualifications. Appointment indexes support dealership, bay, and technician time-range lookups.

## 7. API Contract

| Method | Path | Purpose | Success |
| --- | --- | --- | --- |
| GET | /api/availability | Returns the currently preferred free bay and qualified technician for a service and start time. | 200 |
| POST | /api/appointments | Rechecks availability transactionally and creates a confirmed appointment. | 201 |
| GET | /api/appointments/{id} | Retrieves a previously created appointment. | 200 |

Expected error semantics:

| Status | Meaning |
| --- | --- |
| 400 | Missing or invalid input, past start time, or vehicle/customer mismatch. |
| 404 | Customer context, vehicle, dealership, service type, or appointment was not found. |
| 409 | No bay and qualified technician combination can cover the complete requested duration. |

Concrete cURL requests are maintained in the repository README.

## 8. Booking Data Flow

~~~mermaid
sequenceDiagram
    actor Client
    participant API as AppointmentController
    participant Scheduler as AppointmentSchedulerService
    participant Repo as Domain Repositories
    participant DB as PostgreSQL
    participant Policy as SkillFitPolicy

    Client->>API: POST /api/appointments
    API->>Scheduler: ScheduleAppointmentCommand
    Scheduler->>Repo: Load dealership, customer, vehicle, service type
    Repo->>DB: SELECT reference data
    DB-->>Repo: Reference records
    Scheduler->>Scheduler: Validate ownership and calculate TimeWindow
    Scheduler->>Repo: Lock dealership service bays
    Repo->>DB: SELECT service_bays ... FOR UPDATE
    Scheduler->>Repo: Query bay candidates, qualified technicians, overlaps
    Repo->>DB: SELECT candidates and overlapping appointments
    DB-->>Repo: Candidate projections and overlaps
    Scheduler->>Policy: select(candidates, overlaps)

    alt Resources available
        Policy-->>Scheduler: ResourceAssignment
        Scheduler->>Repo: Save confirmed Appointment
        Repo->>DB: INSERT appointment
        DB-->>Scheduler: Persisted appointment
        Scheduler-->>API: AppointmentView
        API-->>Client: 201 Created + Location
    else No valid combination
        Policy-->>Scheduler: Empty result
        Scheduler-->>API: BookingConflictException
        API-->>Client: 409 Conflict
    end
~~~

The availability endpoint performs the candidate and overlap reads without a write lock because it does not reserve anything. Its result can become stale immediately. Correctness is enforced only by repeating the check in the create transaction.

## 9. Resource Assignment Policy

The assignment algorithm is deterministic:

1. Load every service bay for the requested dealership with its total confirmed appointment count.
2. Load every technician at that dealership who is qualified for the requested service.
3. Count each technician's qualifications.
4. Remove bays and technicians used by any appointment overlapping the complete requested window.
5. Select the free bay with the lowest appointment count, then lowest ID as a tie-breaker.
6. Select the free technician with the fewest qualifications, then lowest ID as a tie-breaker.

Preferring a narrowly qualified technician preserves multi-skilled technicians for services that fewer people can perform. This implements a best-fit strategy rather than first-available allocation.

## 10. Concurrency and Consistency

### Race Being Prevented

Without coordination, two transactions can both read the same free bay and technician before either inserts. Both would then confirm conflicting appointments.

### Implemented Strategy

- The create operation runs in one database transaction.
- After input and reference-data validation, it obtains a PESSIMISTIC_WRITE lock on all service-bay rows for the requested dealership.
- Rows are queried in ID order to keep lock acquisition deterministic.
- Every supported booking requires a service bay, so create transactions for the same dealership are serialized across the authoritative check and insert.
- Transactions for different dealerships lock disjoint rows.
- A PostgreSQL Testcontainers test starts two booking requests together and verifies they never receive the same bay or technician.

### Trade-Off

This strategy favors simple, verifiable correctness. It limits booking write throughput within one dealership even when requests target different time windows. That is acceptable for the assessment and typical human booking rates.

At substantially higher throughput, the next design would use PostgreSQL exclusion constraints over resource ID and a timestamp range, plus a bounded retry that selects another candidate after a conflict. That would allow non-conflicting bookings to proceed concurrently while preserving a database-level final guarantee.

## 11. Scalability, Performance, and Reliability

### Current Measures

- Stateless REST and application components allow multiple application instances.
- PostgreSQL provides durable storage and transaction isolation.
- Flyway makes schema creation deterministic across environments.
- Appointment time indexes reduce overlap-query scanning.
- Candidate queries use lightweight projections instead of loading full technician graphs.
- Resource selection has deterministic tie-breakers.
- A real PostgreSQL integration test validates database-specific locking and migration behavior.

### Growth Considerations

- Add database exclusion constraints for final temporal conflict enforcement.
- Add an idempotency key to POST /api/appointments so client retries cannot create two separate bookings.
- Replace repeated lifetime appointment counts with maintained workload statistics if candidate volume grows.
- Add pagination and retention/archive policy before appointment history becomes large.
- Introduce read replicas only for non-authoritative history queries; availability and booking must use the primary.
- Define transaction and lock timeouts to fail predictably during abnormal contention.
- Add load tests around overlap queries and lock wait time before changing the allocation design.

## 12. Technology Choices

| Technology | Choice | Justification |
| --- | --- | --- |
| Language | Java 17 | LTS baseline, strong type safety, mature concurrency and enterprise ecosystem. |
| Framework | Spring Boot 3.5 | Fast REST/JPA configuration, dependency injection, validation, and transaction management. |
| API | Spring MVC | The workload is database-bound and request/response based; a reactive stack would add complexity without a demonstrated need. |
| Persistence | Spring Data JPA / Hibernate | Reduces repository boilerplate while retaining explicit JPQL for allocation and overlap queries. |
| Production database | PostgreSQL 16 | Durable relational constraints, strong transaction semantics, row locks, and a path to temporal exclusion constraints. |
| Migration | Flyway | Versioned, repeatable environment setup independent of Hibernate schema generation. |
| Build | Maven | Conventional Java build lifecycle and dependency management. |
| Boilerplate reduction | Lombok | Keeps persistence models concise while preserving normal Java accessors and constructors. |
| Unit/API test database | H2 PostgreSQL mode | Fast feedback for service and controller tests. |
| Database integration tests | Testcontainers PostgreSQL | Validates the real migration, SQL behavior, and pessimistic locking instead of trusting an in-memory substitute. |
| Local infrastructure | Docker Compose | Starts the required PostgreSQL service with one command and persistent local data. |

## 13. Observability Strategy

### Assessment Baseline

The current assessment keeps runtime instrumentation intentionally small:

- Spring Boot emits application startup, HTTP framework, transaction, and persistence failures to the console.
- API failures use explicit 400, 404, and 409 responses rather than returning a generic success.
- PostgreSQL has a Docker health check.
- Automated tests provide executable evidence for domain behavior, migrations, and concurrent booking correctness.

No custom metrics, distributed tracing, or Actuator endpoints are claimed as implemented.

### Production Plan

**Logging**

- Emit structured JSON logs.
- Propagate a request/correlation ID from the edge.
- Log booking outcome, dealership ID, service type code, appointment ID, selected resource IDs, duration, and conflict reason.
- Do not log customer names, vehicle registration numbers, or request bodies containing personal data.
- Record lock timeout and database exception categories at appropriate levels.

**Metrics**

- Counter: appointment requests by outcome (confirmed, conflict, invalid, error).
- Counter: availability checks by available/unavailable.
- Timer: end-to-end booking latency.
- Timer: resource lock wait and overlap query duration.
- Gauge: database connection-pool utilization.
- Gauge: booking conflicts per dealership and service type.

**Tracing**

- Add OpenTelemetry instrumentation for HTTP, application service, and JDBC spans.
- Carry trace IDs into structured logs.
- Sample normal traffic and retain all error or high-latency traces.

**Health and Alerting**

- Expose separate liveness and readiness endpoints through Spring Boot Actuator.
- Readiness should include database connectivity and successful Flyway initialization.
- Alert on elevated 5xx rate, p95 latency, lock wait, database pool exhaustion, migration failure, and unexpected conflict-rate changes.

## 14. Security and Data Protection

Authentication is not required by the assessment and is not implemented. A production deployment would:

- use OAuth 2.0/OIDC authentication;
- authorize dealership-scoped access;
- validate that the authenticated customer or employee can access the requested vehicle;
- use TLS in transit and managed encryption at rest;
- move database credentials to a secret manager;
- audit appointment creation without storing unnecessary personal information in logs.

## 15. Testing Strategy

| Test level | Coverage |
| --- | --- |
| Domain unit tests | Best-fit technician selection, least-loaded bay selection, occupied-resource filtering, and no-resource outcome. |
| Application integration tests | Successful booking, duration calculation, fallback to a multi-skilled technician, availability, and conflict behavior. |
| Middleware tests | HTTP request mapping, 201 response, Location header, and confirmed resource response. |
| Migration integration test | Flyway schema creation and successful application use against PostgreSQL. |
| Concurrency integration test | Two simultaneous requests cannot receive the same bay or technician. |

The complete suite runs with:

~~~text
mvn clean test
~~~

Docker must be running because the PostgreSQL integration tests use Testcontainers.

## 16. GenAI Use During Design

GenAI was used as an engineering collaborator, not as an unchecked source of truth.

### Design Workflow

1. I supplied the complete scenario and asked the agent to identify domain entities, endpoint needs, ambiguous requirements, race conditions, and scaling risks.
2. I constrained the architecture to an Onion-style package structure and required entities to remain in the domain.
3. I challenged generated decisions that did not fit the problem, including duplicate repository ports/adapters, first-available resource selection, rate limiting as an early priority, and locking an unrelated coordination object.
4. I asked the agent to compare alternatives and then selected the trade-offs appropriate for this assessment.
5. I verified the resulting design against the code, migration, repository queries, transaction boundaries, and executable tests.

### Examples of Human Direction

| AI proposal or first result | Review and final decision |
| --- | --- |
| Separate domain repository ports, JPA repositories, and adapters | Rejected as unnecessary duplication for this assessment. Kept one Spring Data repository set and documented the coupling trade-off. |
| Select the first free technician | Rejected because it wastes scarce multi-skilled technicians. Replaced with the SkillFitPolicy strategy. |
| Add rate limiting early | Deprioritized because booking correctness and resource contention are the core risks. |
| Lock the dealership row | Challenged because the lock should be associated with constrained booking resources. The final implementation locks service-bay rows. |
| Rely on H2 tests for persistence behavior | Rejected as insufficient for PostgreSQL locking and migration verification. Added Testcontainers coverage. |

### Verification and Ownership

AI-generated changes were reviewed through source inspection, package dependency searches, clean Maven builds, focused unit tests, controller tests, Flyway validation, and a real concurrent PostgreSQL test. Suggestions were removed or rewritten when they added indirection without solving a requirement.

The final architectural choices, accepted limitations, test expectations, and submitted code remain my responsibility.

## 17. Key Risks and Future Work

Prioritized follow-up work:

1. Add database temporal exclusion constraints and bounded allocation retry.
2. Add idempotency support for appointment creation.
3. Implement cancellation and rescheduling while preserving resource consistency.
4. Model opening hours, technician shifts, leave, and bay maintenance.
5. Add authentication and dealership-scoped authorization.
6. Implement the production observability plan.
7. Add OpenAPI documentation and consumer contract tests.
8. Add performance tests with realistic dealership and appointment volumes.

## 18. Requirement Traceability

| Scenario A requirement | Implementation evidence |
| --- | --- |
| Request an appointment for vehicle, service type, dealership, and time | POST /api/appointments and ScheduleAppointmentCommand. |
| Check a bay and qualified technician for the full duration | TimeWindow overlap query, technician qualification query, and SkillFitPolicy. |
| Persist a confirmed appointment with all required associations | Appointment.confirmed factory, AppointmentRepository, PostgreSQL migration, and retrieval endpoint. |
| Persistent backend API | Spring MVC, Spring Data JPA, PostgreSQL, and Flyway. |
| Client-side stub or harness | README cURL examples. |
| Core business tests | Domain, service, controller, migration, and concurrency test suites. |
| Build for the future | Explicit concurrency, scalability, reliability, observability, security, and future-work sections above. |
