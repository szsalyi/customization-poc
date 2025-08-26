Dashboard Customisation – Future-Proof Architecture
1. Background

Currently, our microservices architecture consists of 100+ services communicating primarily through synchronous REST APIs.
While this provides clear request–response flows, it introduces several challenges:

Scalability bottlenecks due to synchronous chains of calls.

Tight coupling between services.

Reduced resilience (one failing service can cascade to the dashboard).

Difficult future extensibility for new dashboard features.

The new requirement is to provide a customisable dashboard in the mobile client that supports:

Personalisation of accounts, partners, and widgets.

Ordering of displayed data (custom order, alphabetical, etc.).

Client-specific version handling (e.g., feature toggles for older apps).

Extensibility for future personalisation needs.

2. Key Concepts
   2.1 Domain Services

These remain the authoritative sources of truth for business data (e.g., Accounts Service, Partners Service, Transactions Service).
They should not contain user preference or personalisation logic, to maintain domain boundaries.

2.2 User Preference Service

A new User Preference Service will store user-specific customisation data outside of domain contexts.
This ensures flexibility for other client components (not just the dashboard) to use personalisation data.

Responsibilities:

Store user’s preferred order of accounts, partners, or widgets.

Manage UI-related preferences (hidden widgets, layout style, etc.).

Expose an API (and events) for other services to consume.

2.3 Dashboard Aggregation Service (suggested rename from customisation)

The “customisation service” should be better named Dashboard Aggregation Service to reflect its role:
aggregating domain data + preferences + client-version context.

Responsibilities:

Fetch data from domain services (accounts, partners, etc.).

Merge with User Preference Service data (ordering, filtering, etc.).

Apply client version rules (e.g., downgrade gracefully if old client cannot display new widget type).

Expose a single aggregated API for the mobile client.

Support event subscription for near-real-time updates.

3. Event-Driven Future-Proofing

Currently, all communication is synchronous REST.
To improve scalability, resilience, and extensibility, we propose introducing event-based communication alongside REST.

3.1 Event Types

Domain Events – emitted by core services (e.g., AccountCreated, PartnerUpdated, TransactionPosted).

Preference Events – emitted by User Preference Service (e.g., DashboardOrderChanged).

Aggregation Events – emitted by Dashboard Aggregation Service (e.g., DashboardUpdated for real-time UI refresh).

3.2 Event Streaming Backbone

Adopt a message broker / event streaming platform (e.g., Kafka, Pulsar, or AWS Kinesis).

Benefits:

Services subscribe only to relevant events.

New features can be added without modifying existing producers.

Supports real-time updates (mobile dashboard can subscribe via WebSockets or push notifications).

4. API & Integration
   4.1 REST (Initial Phase)

Dashboard Aggregation Service provides REST endpoint /dashboard/{userId}.

Aggregates domain service data + preferences.

Client fetches dashboard data on login and refresh.

4.2 Hybrid (Transition Phase)

Services emit events (still keep REST APIs).

Aggregation service maintains a materialised view (cached copy of dashboard data for each user).

Mobile client can query via REST or subscribe to updates.

4.3 Fully Event-Driven (Future)

Dashboard Aggregation Service updates a user-centric materialised view whenever events arrive.

Client subscribes to dashboard update events → near-real-time personalisation.

REST remains as fallback for backward compatibility.

5. Client Version Handling

Introduce Feature Capability Matrix at the Aggregation Layer:

e.g., v1 supports [accounts, partners only].

v2 supports [accounts, partners, widgets].

v3+ supports [custom layouts, advanced ordering].

Aggregation Service enforces graceful degradation:

If new widget is not supported, fallback to default layout.

6. Proposed Service Architecture (Layered)
   +--------------------------------------------------------+
   |                Mobile Client (iOS / Android)           |
   |  - Displays dashboard per user                         |
   |  - Subscribes to updates / calls Aggregation API       |
   +----------------------------+---------------------------+
   |
   v
   +--------------------------------------------------------+
   |              Dashboard Aggregation Service             |
   |  - Aggregates domain + preference data                 |
   |  - Applies client version rules                        |
   |  - Exposes REST + emits events                         |
   +----------------------------+---------------------------+
   |
   +---------------------+-----------------------+
   v                                             v
   +-------------------+                     +---------------------+
   | User Preference   |                     | Domain Services     |
   | Service           |                     | (Accounts, Partners,|
   | - Order/layout    |                     | Transactions, etc.) |
   | - Preference events|                    | - Domain events     |
   +-------------------+                     +---------------------+

7. Benefits of this Approach

Decoupling: Preferences stored separately from domain logic.

Flexibility: Dashboard Aggregation allows new features without breaking clients.

Resilience: Event-driven updates reduce REST dependency.

Future-Proof: Easier to integrate new services, widgets, or external partners.

Client Experience: Near-real-time updates, personalisation, version handling.

8. Data Storage & Performance Considerations

To support a highly personalized, read-heavy dashboard, we recommend using Cassandra DB for persistence and Redis for caching.

Why Cassandra?

Write scalability: Cassandra is designed for high write throughput, which is important when storing large volumes of user preferences, ordering configurations, and widget states across millions of users.

Read performance for personalization: With partition-based access patterns, Cassandra can quickly retrieve per-user or per-segment customisation data. The data model can be optimized to fetch all relevant preferences in a single query.

Fault tolerance & availability: Cassandra’s masterless architecture ensures no single point of failure, which is crucial in banking where uptime is critical.

Horizontal scalability: As the dashboard evolves with new widgets/features, Cassandra can scale out by adding nodes without downtime.

Auditability & history: Cassandra supports time-series schemas, making it well-suited for tracking historical preference changes (audit logs, compliance needs).

Why Redis with Cassandra?

Ultra-fast reads: Redis serves as an in-memory cache, ensuring millisecond response times for the dashboard UI, which is essential for mobile banking applications.

Offloading Cassandra: Instead of hitting Cassandra for every read, Redis handles frequent lookups (e.g., “fetch dashboard for userId=123”).

TTL-based invalidation: Preferences or dashboards that change often (e.g., widget order) can be cached temporarily and refreshed from Cassandra when expired.

Support for materialized views: Redis can store aggregated dashboard snapshots (user preferences + domain data), reducing compute overhead in the aggregation service.

Cassandra + Redis Together

Cassandra = durable, scalable persistence (system of record for preferences).

Redis = performance layer for read-heavy, low-latency queries.

The combination ensures:

Low-latency responses for end-users.

Scalable storage for growing data.

Resilience in case Redis cache misses → fallback to Cassandra.

✅ Next Steps:

Define event schema & contract (Avro/Protobuf recommended).

Implement User Preference Service.

Create Dashboard Aggregation Service with REST API (Phase 1).

Introduce event streaming platform and gradually add subscriptions.

Plan client upgrade strategy with feature capability matrix.

Implementation Architecture Diagrams
High-Level System Architecture

┌─────────────────────────────────────────────────────────────────┐
│                        API Gateway Layer                        │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │   Mobile BFF    │  │    Web BFF      │  │   API Client    │ │
│  │                 │  │                 │  │      BFF        │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Experience Orchestration                      │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │          experience-orchestration-service                   ││
│  │  • Aggregates domain data with user preferences            ││
│  │  • Handles client version compatibility                    ││
│  │  • Manages A/B testing and feature flags                   ││
│  │  • Applies personalization business rules                  ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
                    │                            │
                    ▼                            ▼
┌──────────────────────────────┐    ┌────────────────────────────┐
│    User Preference Layer     │    │     Domain Services        │
│ ┌──────────────────────────┐ │    │ ┌────────────────────────┐ │
│ │ user-preference-service  │ │    │ │   account-service      │ │
│ │ • Raw preference storage │ │    │ │   transaction-service  │ │
│ │ • Consent management     │ │    │ │   investment-service   │ │
│ │ • Version compatibility  │ │    │ │   payment-service      │ │
│ │ • Audit trail           │ │    │ │   (100+ services)      │ │
│ └──────────────────────────┘ │    │ └────────────────────────┘ │
└──────────────────────────────┘    └────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Event Backbone                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │  Apache Kafka   │  │  Event Store    │  │ Schema Registry │ │
│  │  • Real-time    │  │  • Audit trail  │  │ • Version mgmt  │ │
│  │    events       │  │  • Compliance   │  │ • Compatibility │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────┘

Event Flow Sequence Diagram

User App → API Gateway → Experience Orchestration → User Preference Service → Event Store → Kafka → Analytics
    │           │                    │                        │                 │        │        │
    │───────────┼────────────────────┼────────────────────────┼─────────────────┼────────┼────────┼──── Update Dashboard Layout
    │           │                    │                        │                 │        │        │
    │           │────────────────────┼────────────────────────┼─────────────────┼────────┼────────┼──── Route to Experience Service
    │           │                    │                        │                 │        │        │
    │           │                    │────────────────────────┼─────────────────┼────────┼────────┼──── Get User Preferences
    │           │                    │                        │                 │        │        │
    │           │                    │                        │─────────────────┼────────┼────────┼──── Update Preference Store
    │           │                    │                        │                 │        │        │
    │           │                    │                        │                 │────────┼────────┼──── Emit PreferenceUpdated Event
    │           │                    │                        │                 │        │        │
    │           │                    │                        │                 │        │────────┼──── Publish to Topic
    │           │                    │                        │                 │        │        │
    │           │                    │                        │                 │        │        │────── Process Analytics Event
    │           │                    │                        │                 │        │        │
    │───────────┼────────────────────┼────────────────────────┼─────────────────┼────────┼────────┼──── Return Personalized Dashboard

Dashboard Customisation – Future-Proof Architecture
1. Background

Currently, our microservices architecture consists of 100+ services communicating primarily through synchronous REST APIs.
While this provides clear request–response flows, it introduces several challenges:

Scalability bottlenecks due to synchronous chains of calls. Each request to the dashboard may fan out into multiple calls across domain services, increasing latency and infrastructure load.

Tight coupling between services: the dashboard depends directly on multiple domain services being available and performant.

Reduced resilience: a single service outage may block the entire dashboard load.

Difficult extensibility: adding a new widget or preference type requires modifying multiple services and their APIs.

The new requirement is to provide a customisable dashboard in the mobile client that supports:

Personalisation of accounts, partners, and widgets.

Ordering of displayed data (custom order, alphabetical, etc.).

Client-specific version handling (e.g., feature toggles for older apps).

Extensibility for future personalisation needs.

2. Key Concepts
   2.1 Domain Services

Remain the authoritative sources of truth for business data (e.g., Accounts, Partners, Transactions).

Must not embed user preference or personalization logic, to preserve domain boundaries.

2.2 User Preference Service

Stores user-specific customization data outside domain contexts.

Provides flexibility for other client components to reuse personalization data.

Responsibilities:

Store user’s preferred order of accounts, partners, or widgets.

Manage UI-related preferences (hidden widgets, layout style, etc.).

Expose an API and publish events for consumption by other services.

2.3 Dashboard Aggregation Service

Better name for “customisation service.”

Aggregates domain data + preferences + client version context.

Responsibilities:

Fetch domain data (accounts, partners, etc.) and merge with preferences.

Apply client version downgrade rules.

Expose a single API for the mobile client.

Maintain materialized views of dashboards for faster access.

Subscribe to domain and preference events to update views.

Optionally emit DashboardUpdated events for real-time UI refresh.

3. Event-Driven Future-Proofing
   3.1 How Event-Driven Solves Coupling & Resource Usage

Decoupling: Instead of direct synchronous calls from the Aggregation Service to every domain service, each service publishes events (e.g., AccountCreated, PartnerUpdated). The aggregation layer consumes these asynchronously.

Reduced fan-out: The dashboard does not trigger multiple calls every time a user logs in. Instead, it queries its materialized view, already updated by subscribed events.

Lower resource usage:

Domain services are not overloaded by repeated dashboard fetch requests.

Expensive aggregation logic is performed once (on event consumption), not every time a user opens the app.

Resilience: Even if one domain service is temporarily down, events are replayed when it recovers, ensuring eventual consistency. The dashboard still serves cached/last-known data.

3.2 Event Types

Domain Events – AccountCreated, PartnerUpdated, TransactionPosted.

Preference Events – DashboardOrderChanged.

Aggregation Events – DashboardUpdated.

3.3 Data Handling in Event-Driven Model

Each event is persisted in its domain service (system of record).

The User Preference Service stores personalization preferences in Cassandra.

The Dashboard Aggregation Service maintains a materialized view per user (built from domain + preference events).

Redis caches frequently accessed dashboards to serve mobile clients at low latency.

Thus:

Domain = source of truth for business data.

Preferences = source of truth for personalization.

Aggregation = optimized copy for serving dashboard requests.

4. Data Duplication & Double Saving Concerns
   4.1 The Double-Saving Issue

A natural question: if events are stored in both the source service and in the Aggregation Service, is this duplication?

Yes — the Aggregation Service stores derived data that overlaps with the original domain data.

4.2 Why Duplication is Acceptable

Materialized views are a standard event-driven pattern. They intentionally duplicate data in a read-optimized form.

Benefits outweigh storage cost:

Faster queries (one aggregated call instead of multiple REST calls).

Decoupling (aggregation service doesn’t rely on synchronous availability of domain services).

Tailored schema for dashboard use cases (different from domain data).

4.3 Potential Issues

Eventual consistency: Data in the Aggregation Service may lag behind the domain service slightly.

Reprocessing overhead: If the aggregation logic changes, historical events may need to be replayed to rebuild materialized views.

Storage growth: Duplicated data increases storage requirements (mitigated by Cassandra’s scalability).

4.4 Mitigation Strategies

Clearly define ownership: domain services own business data, aggregation service owns dashboard views.

Use idempotent event consumers to avoid duplicates.

Leverage Cassandra for persistence (auditability, scale) and Redis for caching (low-latency reads).

Implement event replay capability to rebuild materialized views when needed.

5. Data Storage & Performance Considerations

Cassandra DB:

Durable, scalable storage for preferences + dashboard materialized views.

Write-heavy optimization → perfect for storing frequent personalization updates.

Supports time-series models for audit/history tracking.

Redis Cache:

Provides sub-millisecond access for dashboard reads.

Stores pre-aggregated dashboard snapshots.

TTL ensures stale data is eventually refreshed from Cassandra.

Together: Cassandra (persistence) + Redis (performance) ensures the architecture can handle read-heavy, personalization-centric traffic.


@startuml
title Event-Driven Dashboard Personalisation Architecture

skinparam componentStyle rectangle

actor MobileClient as MC

component "Dashboard Aggregation Service" as DAS
component "User Preference Service" as UPS
component "Domain Services" as DS
component "Event Streaming Backbone\n(Kafka / Pulsar / Kinesis)" as ESB
database "Preference DB" as PrefDB
database "Domain DBs" as DomainDB
database "Materialised Views\n(Cassandra)" as MV
database "Read Cache\n(Redis)" as Cache

MC -> DAS : REST /dashboard/{user}
MC <- DAS : Dashboard JSON
DAS -> Cache : Fetch user dashboard
DAS -> MV : Fallback fetch (if not cached)
DAS <- UPS : (REST) User preferences
DAS <- DS : (REST) Domain data

UPS -> PrefDB : Store preferences
DS -> DomainDB : Store domain data

UPS --> ESB : -- Event --> PreferenceChanged
DS --> ESB : -- Event --> AccountUpdated, PartnerUpdated
ESB --> DAS : -- Event --> PreferenceChanged, AccountUpdated
ESB --> MV : -- Event --> Update materialised views

DAS -> Cache : Update cache after aggregation
MC <- DAS : (Push/WebSocket) DashboardUpdated event

@enduml


🔎 How Data is Handled

Domain Events

Domain Services (e.g., Accounts, Partners) emit events like AccountCreated, PartnerUpdated.

These events are published to Kafka (or another event bus).

Preference Events

User Preference Service emits events like DashboardOrderChanged.

Stored in Preference DB (Cassandra) and published to event backbone.

Aggregation Layer

Dashboard Aggregation Service subscribes to both Domain Events and Preference Events.

It maintains a materialised view per user in Cassandra.

Redis is used as a high-speed read cache for frequently accessed dashboards.

Client Interaction

On login, the Mobile Client fetches /dashboard/{userId} → served from Redis (fast).

If not cached, fallback to Cassandra materialised view.

Real-time updates: DashboardUpdated events are pushed via WebSocket or Push Notification.

⚠️ Double-Saving Data — Potential Issue

Yes, data is stored twice:

Source of truth in Domain DB (Accounts, Partners, etc.).

Aggregated + denormalised view in Cassandra (for queries).

Why acceptable?

Event-driven materialisation is a CQRS pattern:

Command side = Domain DB.

Query side = Aggregation in Cassandra.

It decouples reads from writes, reducing load on domain services.

Challenges:

Event loss or duplication → materialised view might drift.

Must ensure idempotency when applying events (e.g., re-applying AccountCreated should not duplicate rows).

Use event replay (Kafka retention) to rebuild views when needed.