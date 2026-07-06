🚀 TechMart-Enterprise-Platform

Coursework Module: Business Component Development I (BCD I) - Final Assessment

Architecture Style: Jakarta EE Multi-Tier Enterprise Architecture

Target Environment: Payara Application Server

📝 Project Overview
TechMart is a high-performance, scalable, and resilient multi-tier e-commerce enterprise solution designed to transition a legacy monolithic application into a robust system capable of handling 10,000+ concurrent user requests with sub-second response times.

This prototype demonstrates high-throughput transaction management, asynchronous background processing, and decoupled event-driven messaging utilizing core Java Enterprise technologies.

🏗️ Architectural Blueprints & Components
The system is built as a Multi-module Maven EAR (Enterprise Application Archive) project to ensure isolated classloading, high cohesion, and simplified enterprise deployment:

techmart-ejb (EJB Module): Contains the core enterprise business logic.

Session Beans: Implements Stateless beans for high-throughput lookup services, Stateful beans for conversant workflow state preservation (e.g., Shopping Cart management), and Singleton beans for centralized configuration registries.

Asynchronous Processing: Utilizes @Asynchronous and Future<T> bindings to offload heavy operations such as payment invoices and notifications from the main thread execution pool.

Message-Driven Beans (MDB): Integrated via Java Messaging Service (JMS) utilizing Point-to-Point and Publish/Subscribe messaging patterns for real-time decoupled ordering and messaging sub-systems.

techmart-web (Web WAR Module): Standard lightweight web tier processing HTTP requests and serving real-time system performance and metric analytics dashboards.

techmart-ear (Enterprise Bundle): Bundles both EJB and WAR modules into a single deployment artifact optimized for connection pooling and enterprise thread management.

🛠️ Tech Stack & Infrastructure
Language & Platform: Jakarta EE 8+

Enterprise Runtime: Payara

Database Management: PostgreSQL with optimized Connection Pooling

Build & Dependency Management: Apache Maven (Multi-module Architecture)

Testing Integration: Apache JMeter (Performance/Load Benchmarking)

📈 Core Non-Functional Focus (NFR)
This implementation focuses heavily on validating strict enterprise constraints:

Scalability: Handling massive transaction bursts via JMS queues and decoupled MDBs.

Concurrency Optimization: Thread pool handling and optimized database lifecycle management to counter traditional locking bottlenecks.

Fault Tolerance: Resilient lifecycle state management across distributed components.

