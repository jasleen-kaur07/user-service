# User Service

Part of a group **Ecommerce Platform** built as microservices. This repository holds the
**User Service**: user profiles, customer addresses and basic merchant identity.

---

## Table of contents

1. [Project overview](#1-project-overview)
2. [User Service responsibility](#2-user-service-responsibility)
3. [Architecture](#3-architecture)
4. [Database ER diagram](#4-database-er-diagram)
5. [Database tables](#5-database-tables)
6. [API documentation](#6-api-documentation)
7. [Auth Service integration](#7-auth-service-integration)
8. [Order Service integration](#8-order-service-integration)
9. [Merchant Service integration](#9-merchant-service-integration)
10. [Local PostgreSQL setup](#10-local-postgresql-setup)
11. [Environment variables](#11-environment-variables)
12. [Flyway migrations](#12-flyway-migrations)
13. [Running the service](#13-running-the-service)
14. [Running tests](#14-running-tests)
15. [Swagger / OpenAPI](#15-swagger--openapi)
16. [Git development guidelines](#16-git-development-guidelines)

---

## 1. Project overview

The platform sells products that **multiple merchants** may offer at different prices and
stock levels. A shopper searches for a product, sees how many merchants sell it, opens the
product page, picks a merchant (ranked by a weighted algorithm over stock, price, rating,
sales and reviews), adds to cart and checks out. Merchants get their own portal for
registration and stock management.

Nine services make up the platform, each with its **own database**:

| Service | Owns |
|---|---|
| Auth Service | registration, login, passwords, JWT, OAuth |
| **User Service** (this repo) | **profiles, addresses, basic merchant identity** |
| Product Service | products, descriptions, attributes, USP |
| Merchant Service | merchant offers, stock, price, ratings, merchant ranking |
| Cart Service | carts and cart items |
| Order Service | orders, order items, order status, order history |
| Search Service | product search index |
| Notification Service | emails |
| API Gateway | routing, JWT validation, identity forwarding |

Services talk over **REST** (with OpenFeign where a service needs a typed client) and
**Kafka** for asynchronous events. No service reads another service's database, and there
are no cross-service foreign keys.

---

## 2. User Service responsibility

**Owns**

* the user profile — email, first name, last name, phone
* the user type — `CUSTOMER` or `MERCHANT`
* customer addresses, including the single-default-address rule
* basic merchant identity, and therefore **the `merchantId`** other services key on
* serving all of the above to other services over REST

**Does not own — deliberately**

| Not here | Lives in |
|---|---|
| login, passwords, password hashes, JWT, OAuth, sessions | Auth Service |
| products, descriptions, attributes | Product Service |
| merchant offers, stock, price, ratings, reviews, units sold, ranking | Merchant Service |
| carts, cart items | Cart Service |
| orders, order items, order history | Order Service |
| emails and notifications | Notification Service |

There is **no Kafka producer or consumer in this service**. User operations are simple
request/response, so they use REST; Kafka is used elsewhere in the platform, chiefly for
Order Service events. Nor are there guest-user records: guests browse and use a guest cart
per the Cart Service design, and only registered users get a row here.

---

## 3. Architecture

### Where this service sits

```
                        ┌──────────────┐
                        │   Frontend   │
                        └──────┬───────┘
                               │ HTTPS + JWT
                        ┌──────▼───────┐
                        │ API Gateway  │  validates JWT, injects identity headers
                        └──┬────┬───┬──┘
             ┌─────────────┘    │   └──────────────┐
             ▼                  ▼                  ▼
      ┌────────────┐     ┌────────────┐    ┌─────────────┐
      │  Product   │     │    Cart    │    │    Order    │
      └────────────┘     └─────┬──────┘    └──────┬──────┘
                               │ REST (rare)      │ REST / OpenFeign
      ┌────────────┐           │                  │ userId, email, address
      │    Auth    │  REST     └────────┬─────────┘
      │  Service   │───────────────────►│
      └────────────┘  POST /api/internal/users
                                        ▼
                             ╔═════════════════════╗
                             ║    USER SERVICE     ║  port 8082
                             ╚══════════╤══════════╝
                                        │ JDBC — only this service
                                ┌───────▼────────┐
                                │ user_service_db│  PostgreSQL
                                └────────────────┘
                                        ▲
                                        │ REST: GET /api/merchants/by-user/{userId}
                                ┌───────┴────────┐
                                │ Merchant Svc   │
                                └────────────────┘
```

Note the direction of dependency: **User Service calls nobody.** It is a leaf. That is why
there is no OpenFeign client in this repository — the Feign clients live in the *consuming*
services.

### Internal layering

```
HTTP
 │
 ├── controller/   HTTP only: bind, validate, assert ownership, delegate
 │        ▼
 ├── service/      @Transactional — ALL business rules live here
 │        ▼
 ├── mapper/       entity ⇄ DTO (hand-written static methods)
 │        ▼
 ├── repository/   Spring Data JPA
 │        ▼
 ├── entity/       JPA entities — never leave the service layer
 │        ▼
 └── PostgreSQL    schema owned by Flyway; Hibernate runs ddl-auto=validate

cross-cutting
  exception/  GlobalExceptionHandler → one error shape for every failure
  security/   SecurityConfig, IdentityHeaderFilter, CurrentUser
  config/     OpenApiConfig
```

JPA entities are never returned from a controller. Every response is a DTO, so renaming a
column cannot silently change the published API.

### Design patterns

MVC/layered, Repository, DTO, Mapper, and constructor-based Dependency Injection. The
"singleton" requirement is met by Spring's default singleton bean scope — there are no
hand-written singleton classes, which would only make the code harder to test.

### Project layout

```
user-service/
├── pom.xml
├── README.md
└── src
    ├── main/java/com/ecommerce/userservice/
    │   ├── UserServiceApplication.java
    │   ├── controller/   UserController, AddressController, MerchantController,
    │   │                 MerchantProfileController, InternalUserController
    │   ├── service/      UserService, AddressService, MerchantProfileService
    │   ├── repository/   UserRepository, AddressRepository, MerchantProfileRepository
    │   ├── entity/       User, Address, MerchantProfile, UserType
    │   ├── dto/          requests, responses, ErrorResponse, ValidationPatterns
    │   ├── mapper/       UserMapper, AddressMapper, MerchantProfileMapper
    │   ├── exception/    ErrorCode, ApiException + 4 subclasses, GlobalExceptionHandler
    │   ├── config/       OpenApiConfig
    │   └── security/     SecurityConfig, IdentityHeaderFilter, CurrentUser,
    │                     AuthenticatedUser, RestAuthEntryPoints
    ├── main/resources/   application.yml, application-local.yml, db/migration/V1..V3
    └── test/java/com/ecommerce/userservice/
        ├── service/      3 unit test classes (Mockito)
        ├── controller/   5 MockMvc slice test classes
        └── repository/   DatabaseConstraintIntegrationTest (@Tag("integration"))
```

There is intentionally **no `Dockerfile` and no `docker-compose.yml`** — the service runs
directly from an IDE or Maven against a local PostgreSQL.

---

## 4. Database ER diagram

```
┌───────────────────────────────────────────────┐
│                    users                      │
├───────────────────────────────────────────────┤
│ PK  id            UUID        ← from Auth Svc │
│ UQ  email         VARCHAR(255)  NOT NULL      │
│     first_name    VARCHAR(100)                │
│     last_name     VARCHAR(100)                │
│     phone         VARCHAR(20)                 │
│     user_type     VARCHAR(20)   NOT NULL      │
│                   CHECK IN ('CUSTOMER',       │
│                             'MERCHANT')       │
│     created_at    TIMESTAMPTZ   NOT NULL      │
│     updated_at    TIMESTAMPTZ   NOT NULL      │
└──────┬──────────────────────────────┬─────────┘
       │ 1                            │ 1
       │                              │
       │ M                            │ 0..1
┌──────▼──────────────────────┐  ┌────▼──────────────────────────────┐
│         addresses           │  │       merchant_profiles           │
├─────────────────────────────┤  ├───────────────────────────────────┤
│ PK  id            UUID      │  │ PK  id          UUID ← merchantId │
│ FK  user_id       UUID  NN  │  │ FK,UQ user_id   UUID    NOT NULL  │
│     address_line1 VC(255) NN│  │     business_name  VC(255)  NN    │
│     address_line2 VC(255)   │  │     business_email VC(255)        │
│     city          VC(100) NN│  │     business_phone VC(20)         │
│     state         VC(100) NN│  │     created_at  TIMESTAMPTZ  NN   │
│     country       VC(100) NN│  │     updated_at  TIMESTAMPTZ  NN   │
│     pincode       VC(20)  NN│  └───────────────────────────────────┘
│     is_default    BOOL NN   │
│                   DEFAULT F │   merchant_profiles.id IS the merchantId,
│     created_at TIMESTAMPTZ  │   shared with Merchant Service and
│     updated_at TIMESTAMPTZ  │   Order Service.
└─────────────────────────────┘

FKs (both intra-service, both legal):
  addresses.user_id         → users.id  ON DELETE CASCADE
  merchant_profiles.user_id → users.id  ON DELETE CASCADE

There is no foreign key to any other service's table, and no other service
connects to this database.
```

---

## 5. Database tables

### `users`

The profile. **`id` is not generated here** — it is the UUID Auth Service already minted,
which is what lets every service refer to the same user without a shared database. There is
no password, hash, token or OAuth column, and there never will be.

### `addresses`

Delivery addresses, 1→M from `users`. The one rule worth reading the DDL for:

```sql
CREATE UNIQUE INDEX uq_addresses_one_default_per_user
    ON addresses (user_id)
    WHERE is_default;
```

A **partial** unique index: `user_id` must be unique only among rows where `is_default` is
true. A user may own many addresses but at most one default, and because the database
enforces it, two concurrent "set default" requests cannot both succeed — one gets a
constraint violation. A Java-only check could not promise that.

The service layer upholds the same invariant by **demoting then promoting inside one
transaction**. The order is forced: promoting first would collide with the index.

Deleting the default promotes the oldest remaining address, so a user with addresses always
has one for checkout to preselect.

### `merchant_profiles`

**Basic identity only, and its primary key is the `merchantId`.**

| Present | Absent — belongs to Merchant Service |
|---|---|
| `businessName`, `businessEmail`, `businessPhone` | products, offers, price, stock, inventory |
| the owning `userId` | rating, reviews, units sold, ranking, product mappings |

If a column here ever starts to look like commerce data, it is in the wrong service.

The rule *"only a `MERCHANT` may own a profile"* is enforced in `MerchantProfileService`
rather than in DDL, because it depends on `users.user_type` in a different row; expressing
that in SQL would need a trigger, which is harder to test and to reason about.

### Indexes

| Index | Purpose |
|---|---|
| `pk_users`, `pk_addresses`, `pk_merchant_profiles` | primary keys |
| `uq_users_email` | one account per email |
| `idx_addresses_user_id` | every address read filters by user |
| `uq_addresses_one_default_per_user` | the single-default rule |
| `uq_merchant_profiles_user_id` | one merchant profile per user |
| `ck_users_user_type` (CHECK) | only `CUSTOMER` or `MERCHANT`, even from raw SQL |

Emails are stored trimmed and lower-cased by the service layer, so `A@x.com` and `a@x.com`
cannot become two accounts while the plain unique index still does the work.

---

## 6. API documentation

**Public** endpoints require caller identity and enforce that the caller *is* the user in
the path. **Internal** endpoints are service-to-service and must not be routed from the
public internet by the gateway.

| # | Method | Path | Audience |
|---|---|---|---|
| 1 | `GET` | `/api/users/{userId}` | public (owner) + Order/Cart Service |
| 2 | `PATCH` | `/api/users/{userId}` | public (owner) |
| 3 | `POST` | `/api/internal/users` | **internal** — Auth Service |
| 4 | `POST` | `/api/users/{userId}/addresses` | public (owner) |
| 5 | `GET` | `/api/users/{userId}/addresses` | public (owner) + Order Service |
| 6 | `GET` | `/api/users/{userId}/addresses/{addressId}` | public (owner) + Order Service |
| 7 | `PATCH` | `/api/users/{userId}/addresses/{addressId}` | public (owner) |
| 8 | `PATCH` | `/api/users/{userId}/addresses/{addressId}/default` | public (owner) |
| 9 | `DELETE` | `/api/users/{userId}/addresses/{addressId}` | public (owner) |
| 10 | `GET` | `/api/merchants/{merchantId}` | **internal** — Merchant/Order Service |
| 11 | `GET` | `/api/merchants/by-user/{userId}` | **internal** — Merchant Service |
| 12 | `POST` | `/api/users/{userId}/merchant-profile` | public (merchant owner) |
| 13 | `PATCH` | `/api/users/{userId}/merchant-profile` | public (merchant owner) |
| — | `GET` | `/actuator/health` | infrastructure |

> Endpoints 12 and 13 are additions to the original API list. Without a write endpoint no
> merchant could ever be onboarded and the MERCHANT-only rule would be untestable. They sit
> under `/api/users/{userId}` rather than `/api/merchants` because creating a profile is an
> act a *user* performs on their own account, so the ownership check applies; the
> `/api/merchants` routes are unauthenticated service lookups, and a write there would let
> anything that can reach the port mint a merchant identity.

### Examples

**`GET /api/users/{userId}` → 200**

```json
{
  "id": "11111111-2222-3333-4444-555555555555",
  "email": "jasleen@gmail.com",
  "firstName": "Jasleen",
  "lastName": "Kaur",
  "phone": "9876501234",
  "userType": "CUSTOMER",
  "createdAt": "2026-08-21T22:53:42.292785Z",
  "updatedAt": "2026-08-21T22:54:33.070424Z"
}
```

`firstName`, `lastName` and `phone` are always present as keys, `null` when unset — the
response shape does not change with the data.

**`PATCH /api/users/{userId}`** — the request schema contains only these three fields, so
`userId`, `email` and `userType` are unchangeable by construction rather than by filtering:

```json
{ "firstName": "Jasleen", "lastName": "Kaur", "phone": "9876501234" }
```

An empty body is a 400: an update that changes nothing is almost always a client bug. An
explicitly blank string clears the field.

**`POST /api/users/{userId}/addresses` → 201** (`Location` header points at the new address)

```json
{
  "addressLine1": "123 Main Street",
  "addressLine2": "Apartment 4B",
  "city": "Noida",
  "state": "Uttar Pradesh",
  "country": "India",
  "pincode": "201301",
  "isDefault": true
}
```

**`GET /api/merchants/by-user/{userId}` → 200**

```json
{
  "merchantId": "5fda6728-8af1-47c5-be0e-cfed96b5768b",
  "userId": "99999999-8888-7777-6666-555555555555",
  "businessName": "EasyBuy",
  "businessEmail": "easybuy@gmail.com",
  "businessPhone": "9876543210",
  "createdAt": "2026-08-21T22:56:33.856330Z",
  "updatedAt": "2026-08-21T22:56:33.856333Z"
}
```

### Error format

Every failure — validation, business rule, auth, or unexpected — returns the same shape:

```json
{
  "timestamp": "2026-08-21T22:53:42.385710Z",
  "status": 404,
  "error": "USER_NOT_FOUND",
  "message": "User not found: 11111111-2222-3333-4444-555555555555",
  "path": "/api/users/11111111-2222-3333-4444-555555555555"
}
```

Validation failures add a `fieldErrors` array naming every rejected field at once. No stack
trace, exception class name or SQL fragment is ever returned; unexpected errors become a
bare `INTERNAL_ERROR` and the detail goes to the log.

Branch on `error`, never on `message` — the code is the contract, the prose may be reworded.

| Code | HTTP | When |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Bean Validation failure |
| `BAD_REQUEST` | 400 | malformed UUID or JSON, `userType` outside the enum |
| `UNAUTHORIZED` | 401 | no caller identity on a protected route |
| `FORBIDDEN` | 403 | caller is not the user in the path |
| `USER_NOT_FOUND` | 404 | unknown `userId` |
| `ADDRESS_NOT_FOUND` | 404 | unknown address **or one owned by another user** |
| `MERCHANT_PROFILE_NOT_FOUND` | 404 | unknown `merchantId`, or user not onboarded |
| `EMAIL_ALREADY_IN_USE` | 409 | email belongs to a different `userId` |
| `USER_IDENTITY_MISMATCH` | 409 | sync payload contradicts stored identity |
| `MERCHANT_PROFILE_ALREADY_EXISTS` | 409 | user already has a profile |
| `NOT_A_MERCHANT` | 409 | a `CUSTOMER` tried to create a merchant profile |
| `INTERNAL_ERROR` | 500 | anything unhandled |

An address that exists but belongs to someone else returns **404, not 403**, on purpose: a
403 would confirm to a prober that the id is real.

### Security expectations

The API Gateway validates the JWT and forwards the identity as headers:

```
X-User-Id     required, UUID
X-User-Type   required, CUSTOMER | MERCHANT
X-User-Email  optional
```

`/api/users/**` requires them and additionally enforces caller == path user.
`/api/internal/**` and `GET /api/merchants/**` require none, because their callers are
services rather than end users.

**Stated plainly:** anything that can reach port 8082 directly can claim any identity by
setting a header. That is acceptable only because in a deployed environment this service is
not publicly reachable — the gateway is the sole ingress. Locally it is what makes the whole
API testable with `curl`. Migrating to real JWT verification here means replacing
**one class**, `IdentityHeaderFilter`, and nothing else: everything downstream depends only
on the `AuthenticatedUser` abstraction.

---

## 7. Auth Service integration

Auth Service owns registration, login, passwords, hashing, OAuth and JWT issuing. It
classifies each user as `CUSTOMER` or `MERCHANT`. After registering an identity it calls:

```
POST /api/internal/users
{ "userId": "<uuid>", "email": "user@gmail.com", "userType": "CUSTOMER" }
```

User Service reuses that `userId` verbatim — creating its own would produce a second,
conflicting notion of identity.

**The endpoint is idempotent**, because Auth Service may retry after a timeout, a redeploy
or a replayed request:

| Situation | Result |
|---|---|
| new `userId`, unused email | **201 CREATED** |
| known `userId`, identical email and userType | **200 OK**, no write performed |
| known `userId`, **different** email or userType | **409 `USER_IDENTITY_MISMATCH`** |
| new `userId`, email owned by someone else | **409 `EMAIL_ALREADY_IN_USE`** |

The mismatch case is a conflict rather than an update by design: this endpoint must not
become a back door for changing a login email or promoting a customer to a merchant. Two
concurrent syncs for the same identity are also handled — the loser re-reads the winner and
reports success rather than surfacing a constraint error.

**There is no `USER_REGISTERED` Kafka event and no Kafka consumer here.** Registration is a
synchronous REST call, per the agreed architecture.

### The reverse direction: token introspection

Auth Service is also called *by* this service, over OpenFeign, but only on the token path:

```
GET {AUTH_SERVICE_URL}/api/internal/auth/introspect
Authorization: <the caller's header, passed straight through>
        ◀── { active, userId, email, userType } ──
```

`TokenAuthenticationFilter` runs **after** `IdentityHeaderFilter`, so a request that already
carries gateway-injected `X-User-*` headers never triggers this call. Only a direct caller
holding a raw bearer token does.

| Outcome | Response |
|---|---|
| Auth Service returns an identity | authenticated; the usual ownership checks then apply |
| Auth Service returns 401/403/404, or `active: false` | **401** — the token is genuinely bad |
| Auth Service is unreachable, times out, or 5xx | **503 `UPSTREAM_UNAVAILABLE`** |

The last row matters: a 401 there would claim the caller's credentials are wrong when we
never managed to check them.

> The introspection path and response field names are **assumptions** that must be confirmed
> with the Auth Service developer. See `AuthServiceClient` and `TokenIntrospectionResponse`.

---

## 8. Order Service integration

```
Order Service ──GET /api/users/{userId}────────────────────▶ User Service
              ──GET /api/users/{userId}/addresses/{addrId}─▶ User Service
              ◀────────── email + delivery address ─────────
                     │
                     ▼
              order_service_db   (snapshot: userId, email, address copied in)
```

At checkout Order Service needs the `userId`, the email for the confirmation mail, and the
selected delivery address. It reads them over REST — **never** from `user_service_db` — and
then **copies them into its own database**. That snapshot matters: once an order has shipped
to "123 Main Street", a later edit or deletion of that address must not rewrite delivery
history. It is also why deleting an address here is safe for existing orders.

Order Service owns `orders`, `order_items`, order status and order history. There is no
`order_history` table in this service; a user's order history comes from Order Service.

Order Service may also hold a `merchantId` on an order line and resolve it through
`GET /api/merchants/{merchantId}` to show "sold by EasyBuy".

---

## 9. Merchant Service integration

```
Merchant portal ──POST /api/users/{userId}/merchant-profile──▶ User Service
                                                               mints merchantId M101

Merchant Service ──GET /api/merchants/by-user/{userId}──▶ User Service
                 ◀── { merchantId: M101, businessName: "EasyBuy" } ──
                     │
                     ▼
              merchant_service_db
              merchantId=M101, productId=P501, price=35300, stock=5, rating=4.6
```

Merchant Service knows who is logged in (a `userId` from the JWT) and needs the `merchantId`
to key its own data on. It calls `by-user`, gets `M101`, and from then on owns everything
commercial about that merchant: offers, stock, price, ratings, reviews, sales counts and the
weighted ranking used to order merchants on a product page.

Merchant Service does **not** connect to `user_service_db`. The `merchantId` is the only
thing shared, and it is shared over REST.

### The push: merchantId at onboarding

As well as answering the pull above, this service **pushes** a new merchant identity so
Merchant Service does not have to wait for the merchant's first action:

```
POST /api/users/{userId}/merchant-profile
        │  mint merchantId, INSERT, COMMIT   ← the transaction ends here
        ▼  @TransactionalEventListener(AFTER_COMMIT)
POST {MERCHANT_SERVICE_URL}/api/internal/merchants
{ "merchantId": "…", "userId": "…", "businessName": "EasyBuy" }
```

**Best-effort by design.** Publishing an in-process event and pushing after commit means a
Merchant Service outage can never roll back a merchant's onboarding — the failure is logged
and dropped. User Service stays the source of truth, and Merchant Service can pull the
identical data from `GET /api/merchants/by-user/{userId}` at any time. Treat the push as an
optimisation and the pull as the guarantee.

Merchant Service should treat the endpoint as an **upsert on `merchantId`**: a rename resends
the same payload, and so would a retry. A contact-detail-only edit sends nothing, because
Merchant Service does not hold those fields.

Set `MERCHANT_SYNC_ENABLED=false` to turn the push off; it is already off in the `local`
profile so a developer running this service alone does not get an ERROR line per merchant.

> The endpoint path and payload are **assumptions** that must be confirmed with the Merchant
> Service developer. See `MerchantServiceClient` and `MerchantIdentitySyncRequest`.

### Cart Service

Cart Service associates a cart with the `userId` it already holds from the authenticated
request, so it normally makes **no call at all**. If it genuinely needs user details it can
call `GET /api/users/{userId}`. No extra endpoint was added for it.

### Search and Notification Service

No interaction. Search indexes products, not users; Notification Service is triggered by
Order Service events. No search or notification code belongs here.

---

## 10. Local PostgreSQL setup

PostgreSQL runs **locally**, not in a container managed by this project. There is no
`docker-compose.yml` here by design.

Create the role and the two databases once (the second is only for the integration tests):

```bash
createuser --login --no-superuser --no-createdb --no-createrole user_service
psql -d postgres -c "ALTER ROLE user_service PASSWORD 'user_service_dev_pw';"
createdb -O user_service user_service_db
createdb -O user_service user_service_test_db
```

If your PostgreSQL happens to be running inside a container that another project owns, run
the same commands through it — for example, for a container named `lms-postgres` whose
superuser is `lms_user`:

```bash
docker exec lms-postgres createuser -U lms_user --login user_service
docker exec lms-postgres psql -U lms_user -d postgres \
    -c "ALTER ROLE user_service PASSWORD 'user_service_dev_pw';"
docker exec lms-postgres createdb -U lms_user -O user_service user_service_db
docker exec lms-postgres createdb -U lms_user -O user_service user_service_test_db
```

`user_service_db` is a **separate database** from any other service's, which is what the
ownership rule requires. Sharing a PostgreSQL *server* locally is fine; sharing a *database*
is not.

Do not create the tables by hand — Flyway does it on first startup.

---

## 11. Environment variables

Every value that differs between machines is an environment variable with a local default,
so a fresh clone runs without editing any file.

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8082` | HTTP port. Overridable because nine services run side by side. |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `user_service_db` | database name |
| `DB_USERNAME` | `user_service` | database user |
| `DB_PASSWORD` | *(none in the base config)* | database password |
| `AUTH_SERVICE_URL` | `http://localhost:8081` | Auth Service base URL, for bearer-token introspection |
| `MERCHANT_SERVICE_URL` | `http://localhost:8084` | Merchant Service base URL, for the merchantId push |
| `MERCHANT_SYNC_ENABLED` | `true` (`false` in `local`) | turns the merchantId push on or off |
| `TEST_DB_NAME` | `user_service_test_db` | database for integration tests |
| `SPRING_PROFILES_ACTIVE` | `local` | active profile |

`DB_PASSWORD` has **no default in `application.yml`** — a deployed environment must supply
it. The `local` profile supplies a throwaway default for the laptop-only database, and
`.gitignore` excludes `.env` and `*-secrets.yml` so a real credential cannot be committed.

---

## 12. Flyway migrations

Flyway owns the schema; Hibernate never creates or alters it.

```
src/main/resources/db/migration/
├── V1__create_users_table.sql
├── V2__create_addresses_table.sql
└── V3__create_merchant_profiles_table.sql
```

They run automatically on startup, and `flyway_schema_history` records what was applied.

`spring.jpa.hibernate.ddl-auto=validate` means Hibernate compares the entities against the
schema Flyway built and **refuses to start if they have drifted** — a mapping mistake fails
at boot rather than at 2 a.m. in production.

Rules for adding a migration:

* **never edit an applied migration** — Flyway checksums them and will refuse to start;
  add `V4__...` instead;
* one logical change per file, named `V{n}__snake_case_description.sql`;
* include primary keys, foreign keys, unique constraints, indexes and `NOT NULL`;
* update the entity in the same commit, so `validate` proves the two agree.

---

## 13. Running the service

Requirements: **Java 21**, **Maven 3.9+**, **PostgreSQL 16** running locally.

```bash
cd user-service

# 1. supply the database password (or rely on the local-profile default)
export DB_PASSWORD=user_service_dev_pw

# 2. run
mvn spring-boot:run
```

From an IDE, run `UserServiceApplication` with `DB_PASSWORD` set in the run configuration.

On a different port:

```bash
SERVER_PORT=9082 mvn spring-boot:run
```

Build a jar:

```bash
mvn clean package
java -jar target/user-service-0.0.1-SNAPSHOT.jar
```

Check it is alive, then exercise it. Because identity arrives as headers, the whole API is
reachable with `curl` and no gateway:

```bash
curl -s localhost:8082/actuator/health

USER=11111111-2222-3333-4444-555555555555

# Auth Service's call — no identity headers needed
curl -s -X POST localhost:8082/api/internal/users \
  -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$USER\",\"email\":\"jasleen@gmail.com\",\"userType\":\"CUSTOMER\"}"

# an end-user call — identity headers required
curl -s localhost:8082/api/users/$USER \
  -H "X-User-Id: $USER" -H 'X-User-Type: CUSTOMER'

# ...or a bearer token, which User Service resolves by calling Auth Service over Feign
curl -s localhost:8082/api/users/$USER \
  -H 'Authorization: Bearer <token>'

# add an address (the first one becomes the default automatically)
curl -s -X POST localhost:8082/api/users/$USER/addresses \
  -H "X-User-Id: $USER" -H 'X-User-Type: CUSTOMER' \
  -H 'Content-Type: application/json' \
  -d '{"addressLine1":"123 Main Street","city":"Noida","state":"Uttar Pradesh",
       "country":"India","pincode":"201301"}'

# the cross-user rule: 403
curl -s -o /dev/null -w '%{http_code}\n' localhost:8082/api/users/$USER \
  -H 'X-User-Id: cccccccc-cccc-cccc-cccc-cccccccccccc' -H 'X-User-Type: CUSTOMER'
```

---

## 14. Running tests

```bash
mvn test                          # 95 tests, no database needed
mvn test -P integration-tests     #  9 tests, needs local PostgreSQL
```

**`mvn test`** runs the unit and MockMvc slice tests. They use no database at all, so they
run anywhere in a couple of seconds:

| Suite | Covers |
|---|---|
| `UserServiceTest` | sync creates / is idempotent / rejects identity change / rejects duplicate email, get, partial update, blank clears a field, identity fields untouched |
| `AddressServiceTest` | add, first-address-is-default, promote demotes previous, list, get, update, set default, idempotent set default, delete, delete-default promotes next, foreign address is 404, unknown user |
| `MerchantProfileServiceTest` | create, `CUSTOMER` rejected, duplicate rejected, lookup by merchantId and by userId, unknown user, partial update |
| `UserControllerTest` | 200 / 401 / 403 / 404 / 400, malformed UUID |
| `AddressControllerTest` | all six endpoints, `Location` header, and cross-user read/write/delete all 403 |
| `MerchantControllerTest` | both lookups work with **no** identity headers, and the response carries no stock/price/rating field |
| `MerchantProfileControllerTest` | create, `NOT_A_MERCHANT`, duplicate, validation, cross-user 403, anonymous 401 |
| `InternalUserControllerTest` | 201 vs 200, conflicts, validation, and that the endpoint needs no identity headers |

The controller tests import `SecurityConfig` and `CurrentUser` explicitly, because
`@WebMvcTest` does not pick up a `@Configuration` that merely defines a
`SecurityFilterChain`. Without that import the 401/403 assertions would pass while testing
nothing at all.

**`mvn test -P integration-tests`** runs `DatabaseConstraintIntegrationTest` against real
PostgreSQL, proving the *database* enforces the invariants: a second default address is
rejected, many non-defaults are fine, demote-then-promote succeeds where promote-first would
fail, the bulk demote refreshes `updated_at`, email and merchant-profile uniqueness hold, the
`CHECK` constraint rejects an unknown `user_type` even from raw SQL, and deleting a user
cascades to their addresses.

H2 was deliberately rejected for these: it cannot express a partial unique index, so a green
run there would have proved nothing about the database this service actually uses.
Testcontainers was rejected because the project forbids Docker for development.

---

## 15. Swagger / OpenAPI

With the service running:

* Swagger UI — <http://localhost:8082/swagger-ui.html>
* OpenAPI JSON — <http://localhost:8082/v3/api-docs>

Every endpoint documents its request, response, status codes, validation rules and
authentication expectations, including the idempotency table for the sync endpoint.

The `X-User-Id` and `X-User-Type` headers are modelled as OpenAPI security schemes, so
Swagger UI has an **Authorize** button: type a userId and user type once, and every
subsequent request carries them. That makes the ownership rules explorable from the browser
with no API Gateway running — set a different userId and watch the same call return 403.

Disable both in a deployed environment with
`springdoc.api-docs.enabled=false` and `springdoc.swagger-ui.enabled=false`.

---

## 16. Git development guidelines

Small, meaningful commits, each one buildable on its own:

```
Initialize User Service
Add PostgreSQL, Flyway configuration and schema migrations
Add entities, UserType enum and repositories
Add DTOs, mappers, services, controllers, exception handling and security
Flush before mapping responses so audit timestamps are correct
Add tests: 79 unit/slice tests plus 9 database-constraint integration tests
Add README and integration documentation
```

Conventions:

* the subject line says what changed; the body says **why**, especially for a decision a
  reader would otherwise want to "simplify" later;
* never commit a credential — `.env` and `*-secrets.yml` are gitignored, and `DB_PASSWORD`
  comes from the environment;
* `mvn test` must pass before every commit;
* schema changes add a new `V{n}__` migration and update the entity in the *same* commit, so
  `ddl-auto=validate` proves they agree;
* respect the service boundary. A pull request that adds a product, stock, price, rating,
  cart, order or password column to this service is in the wrong repository.

### What this service must never grow

```
✗ Order Service    ──JDBC──▶ user_service_db
✗ Merchant Service ──JDBC──▶ user_service_db
✗ Cart Service     ──JDBC──▶ user_service_db
✗ User Service     ──JDBC──▶ any other service's database
✗ Kafka producer or consumer for basic user CRUD
✗ tables for orders, carts, products, inventory, offers, ratings,
  payments, notifications, passwords, JWTs or OAuth tokens

✓ every one of the above goes through a REST call to the service that owns it
```
