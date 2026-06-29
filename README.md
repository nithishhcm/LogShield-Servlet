![Java](https://img.shields.io/badge/Java-17-orange)
![Jetty](https://img.shields.io/badge/Jetty-Embedded-blue)
![SQLite](https://img.shields.io/badge/SQLite-Database-green)
![Log4j2](https://img.shields.io/badge/Log4j2-Security-red)
![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)
# LogShield-Servlet — Log4j2 Web Application with Embedded SQLite & Jetty

LogShield-Servlet is a lightweight, high-performance Java Servlet web application demonstrating a zero-install deployment architecture. This project showcases a premium dark glassmorphism user authentication portal backed by an embedded SQLite database, running an internal Jetty servlet container, and utilizing advanced Apache Log4j2 audit log isolation to split operational telemetry from security footprints.

---

## 🏗️ System Architecture

```text
Browser (HTML5 / Modern JS)
    │  
    ├── POST /api/register  →  { username, email, password }
    └── POST /api/login     →  { username, password }
    │
    ▼
Jetty Embedded Server (Port 8080) [LogShield-Servlet Container]
    │
    ├── StaticServlet     ⟶  Serves premium glassmorphism UI (index.html)
    ├── RegisterServlet   ⟶  Parses JSON payload ⟶ UserService.register()
    └── LoginServlet      ⟶  Parses JSON payload ⟶ UserService.login()
    │
    ▼
UserService (Core Business Logic Layer)
    │
    ├── Database Operations ──⟶ Shared SQLite JDBC Connection (users.db)
    │
    └── Dedicated Audit Logging Framework (Log4j2 Event Router)
          │
          ├── [INFO]  ⟶ Successful transactions (registration/login complete)
          ├── [WARN]  ⟶ Security anomalies (duplicate accounts, invalid passwords)
          ├── [ERROR] ⟶ Database failures / exceptions (with full stack traces)
          └── [FATAL] ⟶ Core initialization blockages (unreachable DB at boot)
