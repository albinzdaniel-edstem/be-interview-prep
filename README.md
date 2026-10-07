# Backend services

A Spring Boot backend, built one feature at a time. Each feature has its own pull request.

## Stack

- Java 21 and Spring Boot 3.5
- Maven (the wrapper is included, so Maven does not need to be installed)
- H2 in-memory database for local runs and tests
- Liquibase for every database change
- Lombok

## Run the app

```bash
cd backend
./mvnw spring-boot:run
```

The app listens on `http://localhost:8080`.

Settings come from environment variables. Each one has a local default.

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:h2:mem:app;DB_CLOSE_DELAY=-1` | database address |
| `DB_USERNAME` | `sa` | database user |
| `DB_PASSWORD` | empty | database password |

## Run the tests

```bash
cd backend
./mvnw test
```

Format the code before you commit:

```bash
cd backend
./mvnw spotless:apply
```

## Database changes

Tables are created only by Liquibase changesets in
`backend/src/main/resources/db/changelog/`. Hibernate never changes the schema (`ddl-auto: none`).
Ids are UUIDs. Money is stored as whole cents in a `BIGINT` column.

## Response format

Every response uses the same wrapper.

Success:

```json
{
  "success": true,
  "data": {},
  "message": null
}
```

Error:

```json
{
  "success": false,
  "message": "Validation failed",
  "error": {
    "code": "VALIDATION_FAILED",
    "status": 400,
    "timestamp": "2026-10-07T06:00:00Z",
    "path": "/api/v1/tasks",
    "fieldErrors": [
      { "field": "title", "message": "must not be blank" }
    ]
  }
}
```

- `error.code` is a unique, stable name for the kind of error.
- `error.status` matches the HTTP status of the response.
- `fieldErrors` appears only when one or more fields are invalid.
- Unexpected errors return `INTERNAL_ERROR` with a generic message. Details go to the log only.

## Features

| # | Feature | PR |
|---|---------|----|
| 1 | Task API | |
| 2 | URL shortener | |
| 3 | Authentication and roles | |
| 4 | Product catalog | |
| 5 | Order service | |

## Not included

- **Rate limiting.** It was left out because it is not part of the requirements. A production system
  needs it, so that one client cannot overload the service or abuse sign-in, registration and
  link creation.
