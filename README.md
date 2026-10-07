# Backend services

A Spring Boot backend, built one feature at a time. Each feature has its own pull request.

## Stack

- Java 21 and Spring Boot 3.5
- Maven (the wrapper is included, so Maven does not need to be installed)
- H2 in-memory database for local runs and most tests. The PostgreSQL driver is included, so set
  `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` to run on PostgreSQL.
- Testcontainers, to run the order tests on a real PostgreSQL
- Liquibase for every database change
- Caffeine for the in-memory product cache
- Lombok

## Run the app

Set a signing key for login tokens first. The app does not start without it.

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
cd backend
./mvnw spring-boot:run
```

The app listens on `http://localhost:8080`.

Settings come from environment variables. Only `JWT_SECRET` has no default.

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:h2:mem:app;DB_CLOSE_DELAY=-1` | database address |
| `DB_USERNAME` | `sa` | database user |
| `DB_PASSWORD` | empty | database password |
| `SHORT_LINK_BASE_URL` | `http://localhost:8080` | public address used to build short URLs |
| `JWT_SECRET` | none, required | key that signs login tokens, at least 32 characters |
| `ADMIN_EMAIL` | empty | email of the first admin account (see Authentication) |
| `ADMIN_PASSWORD` | empty | password of the first admin account, at least 8 characters |

## Run the tests

```bash
cd backend
./mvnw test
```

**Docker must be running.** The order tests start a real PostgreSQL database in a container
(`postgres:15-alpine`) and create the schema with the real Liquibase changesets. All other tests use H2.

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

## Task API

Base path: `/api/v1/tasks`. Every endpoint needs a login (see Authentication).

| Method | Path | What it does |
|---|---|---|
| POST | `/api/v1/tasks` | Create a task. Returns 201. |
| GET | `/api/v1/tasks` | List tasks, newest first. Optional `status`, `page`, `size`, `sort`. |
| GET | `/api/v1/tasks/{id}` | Get one task. Returns 404 if it does not exist. |
| PUT | `/api/v1/tasks/{id}` | Replace a task. Returns 404 if it does not exist. |
| DELETE | `/api/v1/tasks/{id}` | Delete a task. Returns 204 with no content, or 404 if it does not exist. |

Task fields:

| Field | Rule |
|---|---|
| `title` | required, at most 100 characters |
| `description` | optional, at most 1000 characters |
| `status` | `TODO`, `IN_PROGRESS` or `DONE`. Defaults to `TODO` on create, required on update |
| `dueDate` | optional, `yyyy-MM-dd`, must not be in the past (today is allowed) |
| `createdAt` | set by the server |

Because a past due date is always rejected, an overdue task can only be updated if its due date
is moved to today or later.

Lists are paged. `size` defaults to 20 and is capped at 100. `sort` takes `field,direction`, for example
`sort=title,asc`.

```bash
curl -X POST http://localhost:8080/api/v1/tasks \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title": "Write report", "dueDate": "2030-01-31"}'

curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/tasks?status=TODO&page=0&size=10&sort=dueDate,asc"
```

## URL shortener

Creating a link and reading its stats need a login. Opening a short link does not.

| Method | Path | What it does |
|---|---|---|
| POST | `/api/v1/links` | Shorten a URL. Returns 201 for a new link, or 200 with the existing link if the URL was shortened before. |
| GET | `/s/{code}` | Redirect to the original URL (302) and count the visit. Open to everyone, no login. Unknown code: 404. Expired code: 410. |
| HEAD | `/s/{code}` | The same redirect headers, but the visit is **not** counted. Link checkers and preview bots send HEAD. |
| GET | `/api/v1/links/{code}/stats` | Original URL, visit count, created date and expiry. Unknown code: 404. |

```bash
curl -X POST http://localhost:8080/api/v1/links \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com/some/long/path", "expiresAt": "2030-01-01T00:00:00Z"}'
```

`expiresAt` is optional and must be in the future. Only `http` and `https` URLs are accepted.

How it behaves, and why:

- **Short codes** are 8 random characters from `A-Z`, `a-z`, `0-9`. They come from `SecureRandom`, so
  they cannot be guessed in order. The database has a unique constraint on the code. On a clash the
  service tries a new code, up to 5 times.
- **Same URL twice** returns the same link, so one URL has one code and one set of statistics. The
  database enforces this with a unique constraint on a SHA-256 hash of the URL. If two requests
  shorten the same new URL at the same moment, one insert wins and the other returns that link.
  The expiry of the first request is kept. If that link has already expired, the new request
  renews it: same code, new expiry, visit count kept.
- **Visits** are counted with one SQL statement, `visit_count = visit_count + 1`. The database does the
  increment, so two visitors at the same moment cannot overwrite each other's count. A test opens one
  link from 50 threads at once and expects exactly 50 visits.
- **The redirect is a 302, not a 301**, with `Cache-Control: no-store`. A browser caches a 301 and
  would skip the service on later visits, so those visits would not be counted.
- **The visit path is `/s/{code}`**, outside `/api/v1`, so that the short URL stays short.

## Product catalog

Every endpoint needs a login (see Authentication). 100 sample products are added at startup when the
`products` table is empty. Set `app.catalog.seed.enabled=false` to turn that off.

| Method | Path | What it does |
|---|---|---|
| POST | `/api/v1/products` | Create a product. Returns 201. |
| GET | `/api/v1/products` | List products with paging, sorting and filters. |
| GET | `/api/v1/products/{id}` | Get one product. Returns 404 if it does not exist. |
| PUT | `/api/v1/products/{id}` | Replace a product. Returns 404 if it does not exist. |
| DELETE | `/api/v1/products/{id}` | Delete a product. Returns 204 with no content, or 404. |

A product has a `name` (up to 200 characters), a `category` (up to 50), a `priceCents`, a `stock`, a
`rating` from 0 to 5 and a `createdAt`. **The price is a whole number of cents** (`2500` is 25.00),
so there are no rounding errors. Stock and price cannot be negative. The database enforces this too.

List parameters. All of them are optional and can be combined in one request:

| Parameter | Meaning |
|---|---|
| `category` | Exact category, ignoring upper and lower case. |
| `minPriceCents`, `maxPriceCents` | Price range. Both ends are included. A minimum above the maximum returns 400. |
| `inStock=true` | Only products with stock above 0. `false` or no value means no restriction. |
| `q` | Text that the name must contain, ignoring case. `%` and `_` match themselves. |
| `page`, `size` | Page number from 0, and page size. `size` defaults to 20 and is capped at 100. |
| `sort` | `field,direction`, for example `sort=priceCents,asc`. Fields: `name`, `category`, `priceCents`, `stock`, `rating`, `createdAt`. Default is `createdAt,desc`. |

The response has `content`, `page`, `size`, `totalElements` and `totalPages`.

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/products?category=home&inStock=true&minPriceCents=1000&maxPriceCents=30000&q=lamp&sort=rating,desc&size=10"
```

How it behaves, and why:

- **Filters** are separate small conditions joined with AND in one query (JPA `Specification`). Any
  combination works, and the total count is for the filtered result.
- **Stable paging.** Many products share a rating or category. The service adds `id` as the last sort
  key, so ties are always ordered the same way and no product repeats or goes missing between pages.
- **Single-product lookups are cached** (`GET /api/v1/products/{id}`). The first lookup reads the
  database. Later lookups of the same product are answered from memory. The cache holds at most
  1000 products, and an entry also expires after 10 minutes.
- **No stale data.** Update and delete remove the cached product **after the database commit**. If it
  were removed before, a reader could load the old row and put it back. When two things happen at the
  same moment, Caffeine finishes a load that is already running before it removes the entry.
- **How to see the cache work.** Start the app with SQL logging and open one product twice:

  ```bash
  cd backend && ./mvnw spring-boot:run -Dspring-boot.run.arguments=--logging.level.org.hibernate.SQL=DEBUG
  curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/products/<id>   # one select in the log
  curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/products/<id>   # no select
  ```

  The tests count the statements in the same way. `ProductCacheTest` shows 20 lookups run one
  statement, and a lookup after an update or delete runs one more and returns the new data.
- **One app copy only.** The cache lives in the memory of one app. With several copies, an update on
  one copy does not clear the other copies until their entries expire (10 minutes at most). A shared
  cache such as Redis would fix this. It is not part of this feature.
- **Name search** uses `LIKE '%text%'`, which cannot use a normal index. It is fine for thousands of
  products. For millions, a PostgreSQL trigram or full-text index would be needed.

## Order service

Every endpoint needs a login (see Authentication). An order takes stock from products in the catalog.

| Method | Path | What it does |
|---|---|---|
| POST | `/api/v1/orders` | Place an order with one or more items. Returns 201, or 200 with the first order if it is a retry. |
| POST | `/api/v1/orders/{id}/cancel` | Cancel your own order and give its stock back. |

Placing an order needs an `Idempotency-Key` header. Use a new value (for example a UUID) for each
new order, and send the **same** value again when you retry the same order.

```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: 6f1c2d9e-3b7a-4c55-9a0e-1d2f3a4b5c6d" \
  -H "Content-Type: application/json" \
  -d '{"items": [{"productId": "<product id>", "quantity": 2}]}'
```

| Situation | Status | `error.code` |
|---|---|---|
| Not enough stock for any item | 409 | `INSUFFICIENT_STOCK` (the message names the product, the requested and the available amount) |
| Unknown product | 404 | `PRODUCT_NOT_FOUND` |
| Missing `Idempotency-Key`, no items, or a quantity below 1 | 400 | `MALFORMED_REQUEST` or `VALIDATION_FAILED` |
| Cancelling an order that is not yours or does not exist | 404 | `ORDER_NOT_FOUND` |

How it works, and why:

- **Stock is never oversold.** Each item is reserved with one SQL statement:
  `update products set stock = stock - :qty where id = :id and stock >= :qty`. The database checks
  the stock and lowers it as one step and locks the row while it does. Two orders at the same moment
  cannot both take the last item. If the statement changes no row, there is not enough stock. The
  database also refuses a negative stock (`CHECK (stock >= 0)`) as a second safety net.
- **All or nothing.** The order row and every stock change run in one transaction. If one item has too
  little stock, the transaction is rolled back, and the items already reserved go back as well.
- **No deadlocks.** Lines for the same product are added up and processed in product id order, so every
  order locks product rows in the same order.
- **How a retry is recognised.** The client sends an `Idempotency-Key` header. The database has a
  unique constraint on (user, key). If an order for that pair exists, it is returned and nothing is
  reserved again. The key is per user, so one user's key can never return another user's order. If two
  identical requests arrive at the same moment, one insert wins and the other is rejected before it
  touches the stock, and it returns the winner's order.
- **Cancelling** changes the status with `update ... where status = 'PLACED'`. Only one of two cancel
  requests at the same moment gets a row back, so the stock is returned once. Cancelling an order that
  is already cancelled changes nothing and returns it.
- **The product cache stays correct.** An order changes stock, so the cached product is removed after
  the commit, the same way as for a product update.
- **Orders keep only the product id.** There is no foreign key to the product, so deleting a product
  never breaks an old order. If the product is gone when an order is cancelled, there is nothing to
  give the stock back to, and a warning is logged.

## Authentication

| Method | Path | Who | What it does |
|---|---|---|---|
| POST | `/api/v1/auth/register` | everyone | Create an account with the `USER` role. Returns 201. |
| POST | `/api/v1/auth/login` | everyone | Returns a bearer token that is valid for 15 minutes. |
| GET | `/api/v1/users/me` | any logged-in user | The caller's own profile. |
| GET | `/api/v1/users` | `ADMIN` only | All users, paged. |

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "ann@example.com", "password": "a long password"}'

TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "ann@example.com", "password": "a long password"}' \
  | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/users/me
```

Everything except register, login and opening a short link needs the `Authorization: Bearer` header.

| Situation | Status | `error.code` |
|---|---|---|
| No token, a broken token or a wrong signature | 401 | `UNAUTHENTICATED` |
| Token older than 15 minutes | 401 | `TOKEN_EXPIRED` |
| Wrong email or password | 401 | `INVALID_CREDENTIALS` |
| Logged in, but the role is not allowed | 403 | `ACCESS_DENIED` |
| Email already registered | 409 | `EMAIL_ALREADY_REGISTERED` |

All of these use the standard JSON error format, never an HTML page.

How it works, and why:

- **No server session.** The login token is a signed JWT (JSON Web Token). The server keeps no state
  between requests, so web and mobile clients can use it and any number of app copies can serve it.
  The token holds only the user id, the role and the times. It is signed with HS256 using
  `JWT_SECRET`. Spring's resource server checks the signature, the issuer and the expiry.
- **Exactly 15 minutes.** Spring allows 60 seconds of clock skew by default. It is set to zero, so a
  token stops working 15 minutes after it was issued.
- **Open endpoints ignore the token header.** Register, login and opening a short link skip the token
  check. A client that still sends its old, expired token can log in again. A broken token on any
  other endpoint still gives 401.
- **Passwords** are hashed with BCrypt and never returned or logged. BCrypt reads only the first 72
  bytes, so longer passwords are rejected instead of being cut short without notice.
- **Same answer for a wrong email and a wrong password.** The password is checked in both cases, so
  the response does not reveal whether an email has an account. Registering a taken email does
  return 409, which does reveal it. That is a deliberate trade-off for a clear sign-up error.
- **Roles.** Registration always creates a `USER`. Extra fields in the request, such as `role`, are
  ignored. The first admin is created at startup from `ADMIN_EMAIL` and `ADMIN_PASSWORD`. If they are
  not both set, no admin exists. If the account already exists, nothing changes.
- **No secrets in the source.** The signing key and the admin password come from the environment.
  The app refuses to start without a signing key of at least 32 characters. Tests use a random key.
- **Who the caller is** comes only from the token, never from the request body or path.

## Features

| # | Feature | PR |
|---|---------|----|
| 1 | Task API | [#8](../../pull/8) |
| 2 | URL shortener | [#9](../../pull/9) |
| 3 | Authentication and roles | [#10](../../pull/10) |
| 4 | Product catalog | [#11](../../pull/11) |
| 5 | Order service | |

## Not included

- **Rate limiting.** It was left out because it is not part of the requirements. A production system
  needs it, so that one client cannot overload the service or abuse sign-in, registration and
  link creation.
