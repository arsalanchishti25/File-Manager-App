# File Manager App

## Overview

This project implements a distributed cloud file management system as part of the **SOFT40051: Advanced Software Engineering** module. The application simulates a cloud infrastructure with load balancing, file storage, and user management capabilities.

## What It Does

The system provides:

- **User authentication and role-based access control** (standard and admin users)
- **File operations** including upload, download, sharing, and deletion with read/write permissions
- **Load balancer** that distributes requests across multiple file storage containers using scheduling algorithms (FCFS, Round Robin, Priority Scheduling, etc.)
- **Distributed file storage** with file chunking, encryption, and CRC32 validation
- **Dual database architecture** using local SQLite for offline caching and remote MySQL for centralised data management
- **Dynamic scaling** via MQTT-based communication between the load balancer and host manager
- **JavaFX-based user interface** for intuitive interaction
- **Terminal emulation** allowing users to execute common Linux commands within containers

## Technology Stack

- **Backend:** Java with Maven
- **GUI:** JavaFX and Scene Builder
- **Databases:** SQLite (local) and MySQL (remote)
- **Infrastructure:** Docker and Docker Compose
- **Testing:** JUnit
- **CI/CD:** Jenkins

## Project Status

Following basic completion, this project continues to be developed as a personal challenge and passion project, with ongoing work on advanced features including conflict handling during database synchronisation and improved scalability mechanisms.

---

*This is a coursework project for NTU's Advanced Software Engineering module (2025/26).*
