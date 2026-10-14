# Project Structure

```text
ninepaws-backend/
├── .agent/
│   ├── agents/
│   ├── rules/
│   ├── skills/
│   ├── workflows/
│   └── ARCHITECTURE.md
├── .github/
├── .mvn/
│   └── wrapper/
│       └── maven-wrapper.properties
├── docker/
│   ├── .dockerignore
│   ├── Dockerfile
│   └── docker-compose.yml
├── docs/
│   ├── Conceptual-ERD.md
│   └── Logical-ERD.md
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── ninepaws/
│   │   │           └── backend/
│   │   │               ├── configs/
│   │   │               ├── constants/
│   │   │               ├── controllers/
│   │   │               ├── dtos/
│   │   │               ├── entities/
│   │   │               ├── enums/
│   │   │               ├── exceptions/
│   │   │               ├── mappers/
│   │   │               ├── repositories/
│   │   │               ├── schedulers/
│   │   │               ├── security/
│   │   │               ├── services/
│   │   │               ├── utils/
│   │   │               └── NinepawsBackendApplication.java
│   │   └── resources/
│   │       ├── db/
│   │       │   └── migration/
│   │       ├── static/
│   │       ├── application-dev.yml
│   │       └── application-prod.yaml
│   └── test/
│       └── java/
│           └── com/
│               └── ninepaws/
│                   └── backend/
│                       └── NinepawsBackendApplicationTests.java
├── .env
├── .env.example
├── .gitattributes
├── .gitignore
├── AGENT.md
├── HELP.md
├── Makefile
├── mvnw
├── mvnw.cmd
└── pom.xml
```
