# AISurv

AISurv is a local-first, cloud-enabled intelligent surveillance platform for institutions such as schools, universities, offices, hospitals, estates, warehouses, and multi-building campuses. Its JavaFX CraftView control centre receives local RTSP camera streams, processes selected frames, detects security-relevant activity, preserves approved evidence, stores structured records, and routes authorised alerts. An optional cloud control plane is planned for tenant and site management, licensing, fleet health, remote summaries, notifications, and approved analytics.

The authoritative architecture specification is in [`design`](design). Delivery progress and acceptance criteria are tracked in [`MILESTONES.txt`](MILESTONES.txt).

## Project Status

AISurv has completed **M0: Trustworthy Prototype** and **M1: Reliable Camera Management Platform is in progress**. The first M1 foundation slice introduces the process and API boundary; this remains a working surveillance prototype, not a production security system.

Implemented and connected:

- CraftView JavaFX and AtlantaFX desktop shell
- Responsive navigation and operational page layouts
- PostgreSQL camera registry with Hibernate, HikariCP, and Flyway
- ONVIF WS-Discovery candidate discovery
- Operator-approved manual RTSP registration
- Environment-based development camera configuration
- JavaCV and FFmpeg RTSP capture with controlled reconnect attempts
- Frame packaging, bounded queue abstraction, and elapsed-time frame-rate control
- OpenCV current-motion/activity gating with reconnect-safe state reset
- ONNX Runtime YOLOv8 person detection
- Strict RTSP/RTSPS URI validation before camera persistence
- Per-camera registry validation so malformed rows do not discard valid cameras
- Latest-frame JavaFX delivery coalescing without an unbounded callback queue
- Time-based event cooldown without suppressing ongoing motion analysis
- Structured motion and person events with preserved severity
- Separate typed camera-health events
- Live frames, current-session events, and camera health in the UI
- Worker-owned cleanup of grabbers, frames, OpenCV matrices, queues, and ONNX sessions
- One application-wide shutdown deadline for bootstrap, camera tasks, stream workers, and native cleanup
- ONNX inference cancellation, bounded FFmpeg network operations, survivor reporting, and duplicate-worker prevention
- Background application bootstrap so database failure does not block the first window
- JUnit 5, Mockito, and optional Testcontainers PostgreSQL coverage
- PostgreSQL, Redis, and MinIO development containers
- Multi-module Maven build with API contract, edge core, desktop, and edge backend modules
- Separate Spring Boot edge process bound to loopback by default
- Versioned `GET /api/v1/health` and `GET /api/v1/cameras` contracts
- Versioned edge commands for ONVIF discovery and camera registration
- Versioned camera edit, enable, disable, and delete commands with optimistic locking
- Database-enforced duplicate display-name and stream-URL rejection
- Edge-owned RTSP, motion, and person-detection workers with dynamic reconciliation
- Local JPEG snapshots consumed by the desktop without duplicate RTSP sessions
- API v1.3 camera-update SSE with bounded fan-out and reset-first subscriptions
- Persisted camera health, last-seen, uptime, reconnect counts, and transition history
- Frozen-source-timestamp detection and stale-snapshot invalidation
- Transactional camera configuration audit records
- Bounded RTSP `DESCRIBE` validation before approval or stream replacement
- Edge-only desktop operation with no Hibernate, RTSP, FFmpeg, OpenCV, or ONNX dependency
- Desktop edge compatibility, degraded, unavailable, and reconnect states
- Camera Management listing, discovery, and registration through the edge API
- Contract, HTTP-client, controller, and packaged separate-process tests

Not yet implemented:

- Durable event and incident persistence
- Zone persistence and zone-aware policies
- Human tracking and behavior analysis
- Evidence snapshots, clips, exports, and retention
- Authentication, users, roles, permissions, and sessions
- Persistent audit logging
- Notification delivery and secure QR device pairing
- Redis application integration
- MinIO evidence integration
- Historical reports and real processing metrics
- Face intelligence
- Production installers and distributed services
- Authenticated camera support and secure credential storage, deferred to M3
- Authenticated ONVIF device/media-profile interrogation, deferred to M3
- Edge-to-cloud synchronization and cloud control-plane services

Unimplemented UI pages deliberately show an unavailable state instead of sample operational data.

## Product Principles

AISurv follows these design principles:

- Capture only what is needed.
- Process only what matters.
- Alert only when it is important.
- Store only useful and policy-approved evidence.
- Display only actionable operational information.
- Protect sensitive capabilities through permissions, auditing, and privacy controls.
- Scale without changing the product purpose.

Face intelligence is optional, disabled by default, restricted to approved zones and purposes, permission-controlled, and subject to auditing. Recognition results must express uncertainty and must not be treated as unquestionable identity decisions.

## Architecture

AISurv uses a layered event-driven pipeline:

```text
RTSP camera sources
        |
Camera registry and stream sessions
        |
Stream capture
        |
Frame processing and bounded queues
        |
Motion/activity gate
        |
Object detection and zone-aware filtering
        |
Human tracking and movement analysis
        |
Behavior and optional identity analysis
        |
Event rules and severity classification
        |
Typed event dispatch
   _____|______________________________
  |          |          |       |      |
GUI      Database   Evidence  Alerts  Analytics/Audit
```

The camera pipeline produces structured events. It must not directly control persistence, evidence, notifications, analytics, or the full application lifecycle.

The intended application boundary is:

```text
Local site / edge                              Optional cloud control plane

[CraftView JavaFX client process]
    |
Versioned command/query API and SSE
    |
[Modular edge backend process]
Application services and event-driven surveillance core
    ^
    |
RTSP cameras -> Capture, AI, and event workers
    |
Local PostgreSQL, Redis, evidence storage
    |
Durable outbox and synchronization gateway
    | outbound authenticated HTTPS
    +------------------------------------------> Cloud API and identity boundary
                                                  |
                                                  +-> Tenant, site, and fleet services
                                                  +-> Managed PostgreSQL
                                                  +-> Notifications and approved analytics
```

The desktop UI and local edge backend are separate processes, even when installed on the same workstation. API v1.3 provides health and camera-management contracts, edge-owned stream/AI workers, local JPEG snapshots, persisted runtime health, and camera-update SSE. The desktop reconnects with bounded backoff, refreshes authoritative inventory after every connection and invalidation, and pauses snapshot requests while disconnected. It never opens PostgreSQL, RTSP, FFmpeg, OpenCV, or ONNX resources, so the edge is the single monitoring owner.

The local edge remains operational when the cloud is unavailable. Camera ingestion, AI inference, local alerts, incident handling, operational PostgreSQL, and evidence preservation do not depend on an internet connection. Cloud synchronization is asynchronous, resumable, policy-controlled, and safe to retry.

Neither the desktop client nor the edge runtime connects directly to a cloud database. They communicate with authenticated AISurv APIs, and edge connections are outbound-only by default. Supabase may be used as an early managed PostgreSQL and cloud platform provider, but its database and service-role credentials remain behind the cloud backend. The architecture remains portable to other managed PostgreSQL providers.

## Service Strategy

AISurv establishes three coarse deployment units early:

- JavaFX desktop client
- Local edge backend
- Optional cloud control plane

The edge backend and cloud backend begin as separate modular monoliths, not collections of small microservices. Their modules use explicit ownership, internal interfaces, and versioned external contracts. Capture, inference, evidence, notification, analytics, or synchronization modules are extracted only when measured scaling, fault-isolation, hardware-placement, release-cadence, or team-ownership requirements justify separate deployment.

RTSP capture and AI inference remain colocated on the edge initially because transporting full video frames between network services adds bandwidth, latency, ownership, and backpressure complexity. Extracted services must own their persistence and may not share mutable database tables.

## Technology Baseline

| Area | Technology |
|---|---|
| Runtime | Java 21 |
| Desktop UI | JavaFX 21 and AtlantaFX |
| Local edge backend | Separate Spring Boot 3.5 runtime, introduced during M1 |
| Cloud backend | Authenticated Java service, introduced with the optional control plane |
| Persistence | Hibernate/Jakarta Persistence |
| Local operational database | PostgreSQL 16 |
| Cloud control database | Managed PostgreSQL behind AISurv APIs; provider-neutral, with Supabase as an option |
| Migrations | Flyway |
| Connection pooling | HikariCP |
| RTSP/video | JavaCV, FFmpeg, and OpenCV |
| AI inference | ONNX Runtime Java |
| Current model | YOLOv8n ONNX |
| Shared transient state | Redis, integration scheduled for M1 |
| Evidence storage | Local filesystem or MinIO first; optional approved cloud object storage |
| Edge-cloud synchronization | Authenticated HTTPS, durable outbox, idempotent APIs, and retry with backoff |
| Logging | SLF4J and Logback |
| Testing | JUnit 5, Mockito, and Testcontainers |
| Development services | Docker Compose |
| Desktop packaging | `jpackage`, scheduled for M6 |
| Enterprise orchestration | Kubernetes and Helm, scheduled for M7 |

## Data Ownership

The intended ownership rules are:

| Data | Authority | Cloud handling |
|---|---|---|
| Camera credentials | M3 local secret store | Never synchronize plaintext credentials |
| Camera configuration | Local PostgreSQL | Optional redacted inventory and approved policy sync |
| Current camera status | Edge Redis plus local event history | Synchronize health summaries when enabled |
| Events and incidents | Local PostgreSQL | Synchronize policy-approved summaries or records |
| Evidence metadata | Local PostgreSQL | Optional approved metadata replication |
| Evidence files | Local filesystem or MinIO | Local by default; explicit approved upload only |
| Local users and permissions | Local PostgreSQL | Optional site identity mapping |
| Tenants, subscriptions, and fleet state | Cloud PostgreSQL | Cloud authoritative; cache required edge entitlements locally |
| Notification history | Originating PostgreSQL | Reconcile delivery state through APIs |
| Audit logs | Originating PostgreSQL | Approved summaries may be replicated append-only |
| AI model settings | Local PostgreSQL | Signed, versioned cloud policy may provide updates |

Local PostgreSQL remains authoritative for site operation. Cloud PostgreSQL remains authoritative for tenant, subscription, and fleet-level data. Redis must never be the only location for critical records, and large media must not be stored in relational rows.

## Event Model

The M0 event engine currently emits structured motion and person events. Event type, severity, camera, message, and occurrence time remain typed through dispatch and the UI boundary.

Target severity semantics:

- Low: ordinary informational events or normal movement
- Medium: loitering warnings or impaired camera state
- High: restricted-zone entry, after-hours movement, or unexpected group running
- Critical: fight, major commotion, suspected fall, accident, or emergency

Camera connection and worker state use a separate camera-health model and are not inferred from surveillance-event message text.

## Runtime Requirements

- JDK 21
- Maven 3.9 or newer
- Docker Desktop or another Docker runtime for development infrastructure
- Network access to configured RTSP cameras
- `models/yolov8n.onnx` available relative to the application working directory

Verify the toolchain:

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
mvn -version
docker version
```

Both `java -version` and the Java runtime reported by `mvn -version` must be Java 21. Maven Enforcer rejects other Java feature releases.

## Development Infrastructure

The current development environment represents the local edge. Start its PostgreSQL, Redis, and MinIO services:

```powershell
docker compose up -d
```

Check service state:

```powershell
docker compose ps
```

Development endpoints:

| Service | Endpoint |
|---|---|
| PostgreSQL | `localhost:5432` |
| Redis | `localhost:6379` |
| MinIO API | `localhost:9000` |
| MinIO console | `localhost:9001` |
| AISurv edge API | `127.0.0.1:8080` when the edge process is running |

The credentials in `docker-compose.yml` are development-only. They must not be reused for commercial deployments.

No cloud service is required to run the current local build. The cloud API, synchronization gateway, tenant services, and cloud database are target architecture and have not yet been implemented.

Stop services:

```powershell
docker compose down
```

Use `docker compose down -v` only when intentionally deleting all local development data.

## Database Configuration

Supported environment variables:

| Variable | Development default |
|---|---|
| `AISURV_DB_URL` | `jdbc:postgresql://localhost:5432/aisurv` |
| `AISURV_DB_USER` | `aisurv` |
| `AISURV_DB_PASSWORD` | `aisurv_dev_password` |
| `AISURV_EDGE_BIND_ADDRESS` | `127.0.0.1` |
| `AISURV_EDGE_PORT` | `8080` |
| `AISURV_EDGE_BASE_URI` | `http://127.0.0.1:8080` |

Startup behavior:

1. The CraftView shell opens immediately.
2. The desktop probes the local edge without opening database or camera resources.
3. The edge runs Flyway, owns PostgreSQL access, and starts enabled registry cameras.
4. The desktop reconnects to the edge with bounded backoff and refreshes inventory after reconnect.
5. PostgreSQL or edge failures are shown as degraded/unavailable states without starting a second monitoring path.
6. Invalid persisted camera rows are skipped individually; other valid registry cameras remain authoritative.

Database and RTSP connection attempts use bounded timeouts. The status bar reports connecting, ready, degraded, unavailable, or incompatible state rather than assuming the edge is connected.

## Camera Configuration

Camera configuration comes from the site's local PostgreSQL registry through the edge API. Camera URLs must use `rtsp` or `rtsps`, include a network host, omit embedded credentials, and pass a bounded RTSP `DESCRIBE` probe before approval. Authenticated cameras are intentionally rejected until M3 provides an OS-backed local secret store.

## Camera Discovery And Registration

The Camera Management page performs ONVIF WS-Discovery and manual RTSP registration through the local edge API.

Current registration flow:

1. Discover candidate ONVIF devices or select manual registration.
2. Select a candidate.
3. Enter a display name and RTSP URL.
4. Assign available location metadata and priority.
5. Validate RTSP/RTSPS syntax and connectivity, then save the enabled camera to PostgreSQL.
6. Edit, enable, disable, or delete selected registry cameras through the edge API.
7. The edge incrementally starts, replaces, or stops only the affected worker.

Discovered devices are candidates, not trusted cameras. Registration validates URI syntax, scheme, host, credential absence, and RTSP protocol reachability. ONVIF WS-Discovery is automated; authenticated device/media-profile interrogation and real-vendor acceptance require the M3 credential store and representative camera hardware.

CLI discovery:

```powershell
mvn -f edge-core/pom.xml exec:java -Dexec.mainClass=org.example.aisurv.camera.CameraRegistryTool -Dexec.args="discover"
```

CLI registration:

```powershell
mvn -f edge-core/pom.xml exec:java -Dexec.mainClass=org.example.aisurv.camera.CameraRegistryTool -Dexec.args="register Reception rtsp://user:password@host/stream HIGH"
```

## Build And Run

Build all modules and install their development artifacts:

```powershell
mvn clean install
```

Run the edge backend in one terminal:

```powershell
java -jar edge-backend/target/aisurv-edge-backend-1.0-SNAPSHOT.jar
```

Run the desktop control centre in another terminal:

```powershell
mvn -f desktop/pom.xml javafx:run
```

The edge exposes only loopback HTTP during this M1 slice. Non-loopback binding is rejected until service authentication and TLS are implemented.

IntelliJ may print a very long generated Java classpath before launch. That command line is normal and is not an application failure.

## Tests

The default test suite covers:

- Environment camera-list parsing and ordering
- Camera registration validation and repository delegation
- ONVIF discovery response parsing
- Event-rule evaluation and event-engine severity
- Time-based event cooldown behavior
- Frame queue drop policies and resource release
- Elapsed-time frame-rate controller behavior
- OpenCV motion reset and resolution-change behavior
- JavaCV/OpenCV frame-ownership boundaries
- Concurrent latest-frame JavaFX delivery coalescing
- Globally bounded multi-worker shutdown
- Versioned JSON contract compatibility and redaction
- Desktop edge HTTP compatibility and failure classification
- Camera-update SSE parsing, reset ordering, media-type validation, and reconnect refresh
- Edge-owned camera discovery and registration command mapping
- Edge runtime reconciliation, startup retry, and snapshot publication
- Desktop edge-monitoring capability selection and snapshot HTTP behavior
- Single-flight discovery, bounded request timing, and structured command errors
- Loopback binding enforcement and sanitized API errors
- Packaged edge startup in an independent JVM

Run unit and controller tests:

```powershell
mvn test
```

Run the complete suite, including packaged separate-process verification:

```powershell
mvn verify
```

The PostgreSQL Testcontainers migration/repository test is opt-in so normal development does not require Docker:

```powershell
$env:AISURV_RUN_CONTAINER_TESTS = "true"
mvn clean test
$env:AISURV_RUN_CONTAINER_TESTS = $null
```

The current suite contains 123 unit/controller test cases plus one packaged separate-process integration test. One of the 123 is the PostgreSQL Testcontainers migration/persistence test and is skipped unless explicitly enabled.

## M0 Scope

M0 stabilizes the prototype rather than adding later commercial features.

M0 implementation objectives:

- Establish automated unit and integration test infrastructure.
- Keep database and network startup work off the JavaFX Application Thread.
- Bound database connection attempts and report degraded startup accurately.
- Validate camera and database configuration without hiding non-connectivity failures.
- Preserve structured event type and severity end to end.
- Separate surveillance events from camera-health events.
- Close native video, matrix, queue, and inference resources deterministically.
- Stop tracked background work and stream workers against one application-wide shutdown deadline.
- Cancel active inference and bound FFmpeg network operations so worker-owned native cleanup can complete.
- Remove dead UI implementations and fabricated operational data.
- Use contextual, redacted SLF4J logging.
- Keep documentation and milestone evidence current.

M0 exit criteria and completion evidence are recorded in `MILESTONES.txt`.

## Scaling Strategy

Planned deployment modes:

| Mode | Approximate size | Intended topology |
|---|---:|---|
| Compact edge | 5-10 cameras | Installed desktop plus local single-server services; optional cloud enrollment |
| Medium edge | 10-50 cameras | Separate local capture and AI processing from the UI; optional cloud control plane |
| Enterprise hybrid | 50-100+ cameras per site | Distributed local workers and storage with multi-site cloud fleet management |

Under load, critical cameras retain the strongest monitoring. High-priority cameras receive limited reduction, normal cameras reduce processing before critical zones, and low-priority cameras may use delayed or reduced analytics. Priority scheduling is implemented in M2.

Cloud connectivity is never part of the critical camera-processing path. Sites queue approved synchronization data during an outage and resume through a durable outbox and idempotent APIs. Kubernetes is deliberately deferred until service boundaries and commercial scaling requirements are stable. It is an M7 cloud and enterprise deployment target, not an M0 requirement.

M1 establishes the desktop-to-edge process boundary. Later milestones extract additional workers only in response to measured scaling or reliability requirements.

## Security And Privacy Direction

Commercial requirements include:

- Authentication and controlled sessions
- Role-based access to feeds, events, evidence, settings, and biometric data
- Secure camera credential references
- Audit records for camera changes, evidence access/export, role updates, threshold changes, device enrollment, acknowledgement, and escalation
- Expiring QR pairing tokens that cannot independently grant access
- Policy-based evidence capture and retention
- Restricted, optional, and uncertainty-aware face intelligence
- Externalized production secrets and encrypted service connections
- Outbound-only edge connectivity by default with a unique authenticated site identity
- Tenant and site isolation for every cloud record and command
- Local-by-default camera credentials, raw streams, biometric templates, and evidence
- Explicit policy, legal basis, retention, and authorization before personal data or evidence is uploaded
- No cloud database credentials or Supabase service-role keys in desktop or edge binaries

These controls are not yet complete. The current M1 build must not be deployed as a production security or biometric system.

## Observability Direction

M5 will provide real metrics for:

- Camera uptime
- Frame-processing rate
- AI inference latency
- Queue depth and dropped frames
- PostgreSQL and Redis latency
- Notification failures
- Evidence storage usage
- CPU, GPU, and memory load

Until those measurements exist, the UI reports them as unavailable rather than generating sample values.

## Milestones

| Milestone | Deliverable |
|---|---|
| M0 | Trustworthy prototype |
| M1 | Reliable camera-management platform |
| M2 | Durable event-driven surveillance core |
| M3 | Secure incident and evidence system |
| M4 | Operational notification platform |
| M5 | Advanced intelligence and real observability |
| M6 | Commercial small-institution release, deployed as a local edge |
| M7 | Enterprise hybrid and selectively distributed release with the optional cloud control plane |

Detailed tasks, dependencies, exit criteria, decision records, and blocker records are maintained in [`MILESTONES.txt`](MILESTONES.txt).

## Current Transitional Limitations

- Events exist only for the current desktop session.
- Incident acknowledgement is not persistent.
- Camera health is persisted in PostgreSQL and projected through API v1.3; Redis remains non-authoritative and unused.
- The frame queue remains in the camera worker path; asynchronous processing is M2 work.
- One person detector is initialized per camera worker.
- Person inference failures keep camera health impaired until a later inference succeeds; video can remain available while analytics are impaired.
- Live-frame rendering keeps only the newest pending frame per camera rather than displaying every processed frame.
- Edge monitoring applies registered, edited, enabled, disabled, and deleted cameras dynamically.
- ONVIF discovery does not authenticate or resolve media profiles; authenticated interrogation is deferred to M3.
- Credential-bearing RTSP URLs and credential references are rejected in this release.
- Redis and MinIO run as development services but are not yet used by application code.
- The edge exclusively owns camera queries, configuration, persistence, streams, AI, snapshots, health, audit, and camera-update notifications.
- The packaged edge has a slow cold start because the transitional edge-core includes native video runtimes before it owns stream processing.
- Cloud APIs, tenant services, edge enrollment, durable synchronization, and the cloud database are not implemented.
- The model path is relative to the current working directory.
- Authentication, authorization, evidence, notifications, audit, and reports are not implemented.
- Native libraries cannot be forcibly terminated safely from Java; operations that outlive the shutdown deadline are reported and run only on daemon workers so they cannot hold the process open.

## Troubleshooting

### PostgreSQL unavailable

Start the development services:

```powershell
docker compose up -d
docker compose ps
```

If PostgreSQL remains unavailable, the edge reports degraded health and the desktop remains an API-only control client.

### No cameras configured

Register a credential-free camera through Camera Management and ensure the edge can complete its RTSP validation probe.

### Person detection unavailable

Confirm that `models/yolov8n.onnx` exists and that the process working directory is the project root. Camera streaming can continue in impaired mode without person inference.

An impaired camera can still have a connected video feed. Health returns to online only after person inference succeeds again; receiving a frame alone does not clear analytics impairment.

### JavaFX warnings in IntelliJ

Classpath and native-access warnings from JavaFX or JavaCV are runtime/toolchain warnings, not necessarily launch failures. Application errors are logged separately with AISurv class names and severity.

### Enable detailed diagnostics

Change the relevant logger to `DEBUG` in `src/main/resources/logback.xml` for local diagnosis. Never publish logs containing credentials or full RTSP URLs.
