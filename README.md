# University Management System

A Spring Boot REST API with a small browser frontend for managing the three kinds of university users: **Admins**, **Teachers** and **Students**.
It covers secure login, role-based permissions, full CRUD (Create, Read, Update, Delete), a MySQL database, Docker, automated tests and a GitHub Actions CI pipeline.

This README explains **how each part works and why it is built that way**, so you can explain the project to someone else.

---

## Table of Contents

1. [Technology Stack](#1-technology-stack)
2. [Project Structure](#2-project-structure)
3. [Big Picture: How a Request Travels Through the App](#3-big-picture-how-a-request-travels-through-the-app)
4. [Layer by Layer](#4-layer-by-layer)
   - [4.1 Entity: `User` and `Role`](#41-entity-user-and-role)
   - [4.2 Repository: `UserRepository`](#42-repository-userrepository)
   - [4.3 DTOs: request and response objects](#43-dtos-request-and-response-objects)
   - [4.4 Service: `UserService`](#44-service-userservice)
   - [4.5 Controllers](#45-controllers)
   - [4.6 Error Handling: `GlobalExceptionHandler`](#46-error-handling-globalexceptionhandler)
   - [4.7 Startup Data: `DataInitializer`](#47-startup-data-datainitializer)
5. [Security in Depth](#5-security-in-depth)
6. [The Frontend](#6-the-frontend)
7. [Configuration and Environment Variables](#7-configuration-and-environment-variables)
8. [Running the Project](#8-running-the-project)
9. [API Reference](#9-api-reference)
10. [Testing](#10-testing)
11. [Continuous Integration with GitHub Actions](#11-continuous-integration-with-github-actions)
12. [Likely Questions and Answers](#12-likely-questions-and-answers)
13. [Known Limitations and Possible Improvements](#13-known-limitations-and-possible-improvements)

---

## 1. Technology Stack

| Technology | Version | What it does in this project |
|---|---|---|
| Java | 21 | Programming language |
| Spring Boot | 4.1.2 | Framework that wires everything together and starts an embedded web server (Tomcat) |
| Spring Web MVC | (Boot) | Turns Java methods into HTTP endpoints (`@RestController`, `@GetMapping`, ...) |
| Spring Data JPA + Hibernate | (Boot) | Maps Java objects to database tables and writes the SQL for you |
| Spring Security | (Boot) | Login, sessions, password hashing, CSRF protection, role checks |
| Jakarta Validation | (Boot) | Checks incoming data (`@NotBlank`, `@Email`, `@Size`) |
| Spring Boot Actuator | (Boot) | Provides `/actuator/health` for health checks |
| MySQL | 8.4 | The real database |
| H2 | (Boot) | In-memory database used **only by the tests** |
| JUnit 5 + Mockito | (Boot) | Testing framework and mocking library |
| Maven (wrapper `mvnw`) | 3.10 | Build tool: downloads libraries, compiles, runs tests, builds the jar |
| Docker + Docker Compose | – | Runs the app and MySQL in containers with one command |
| GitHub Actions | – | Runs the tests automatically on every push |
| HTML + CSS + plain JavaScript | – | Single-page frontend (no framework) |

---

## 2. Project Structure

```
university-management/
├── .github/workflows/ci.yml          ← GitHub Actions pipeline
├── compose.yaml                      ← Docker Compose: app + MySQL
├── Dockerfile                        ← How to build the app's Docker image
├── .env                              ← Passwords and settings used by Docker Compose
├── pom.xml                           ← Maven: dependencies + build plugins
├── mvnw / mvnw.cmd                   ← Maven wrapper (no need to install Maven)
└── src/
    ├── main/
    │   ├── java/com/university/management/
    │   │   ├── UniversityManagementApplication.java   ← main() — starts everything
    │   │   ├── config/
    │   │   │   ├── SecurityConfig.java                ← all security rules
    │   │   │   └── DataInitializer.java               ← creates the first admin
    │   │   ├── controller/                            ← HTTP endpoints
    │   │   │   ├── AuthController.java                (login / logout / me / csrf)
    │   │   │   ├── AdminController.java               (/api/admins)
    │   │   │   ├── TeacherController.java             (/api/teachers)
    │   │   │   └── StudentController.java             (/api/students)
    │   │   ├── dto/                                   ← shapes of JSON in/out
    │   │   │   ├── CreateUserRequest.java
    │   │   │   ├── UpdateUserRequest.java
    │   │   │   ├── LoginRequest.java
    │   │   │   └── UserResponse.java
    │   │   ├── entity/                                ← database tables
    │   │   │   ├── User.java
    │   │   │   └── Role.java
    │   │   ├── exception/                             ← custom errors + JSON error responses
    │   │   │   ├── DuplicateEmailException.java
    │   │   │   ├── ResourceNotFoundException.java
    │   │   │   └── GlobalExceptionHandler.java
    │   │   ├── repository/
    │   │   │   └── UserRepository.java                ← database queries
    │   │   └── service/
    │   │       ├── UserService.java                   ← business logic
    │   │       └── CustomUserDetailsService.java      ← lets Spring Security find users
    │   └── resources/
    │       ├── application.properties                 ← app configuration
    │       └── static/                                ← frontend, served as-is
    │           ├── index.html
    │           ├── css/style.css
    │           └── js/app.js
    └── test/
        ├── java/com/university/management/
        │   ├── service/                               ← 6 UNIT TESTS
        │   │   ├── UserServiceTest.java               (4 tests)
        │   │   └── CustomUserDetailsServiceTest.java  (2 tests)
        │   └── integration/                           ← 3 INTEGRATION TESTS
        │       ├── IntegrationTestSupport.java        (shared setup + helpers)
        │       ├── AuthenticationFlowIT.java
        │       ├── StudentCrudIT.java
        │       └── RoleBasedAccessIT.java
        └── resources/
            └── application-test.properties            ← test settings (H2 database)
```

This is the standard **layered architecture**. Each package has one job:

| Layer | Package | Responsibility | Talks to |
|---|---|---|---|
| Presentation | `controller` | Receive HTTP, validate input, return HTTP | Service |
| Business | `service` | Rules: no duplicate emails, hash passwords, check password length | Repository |
| Data access | `repository` | Read and write the database | Database |
| Model | `entity` | What a row in the table looks like | – |
| Transfer | `dto` | What the JSON looks like | – |
| Cross-cutting | `config`, `exception` | Security, startup data, error formatting | Everything |

**Why use layers?** Each layer can be changed or tested on its own. You could swap MySQL for PostgreSQL and only the configuration changes. The unit tests in this project test the service layer without a database, which is only possible because the layers are separate.

---

## 3. Big Picture: How a Request Travels Through the App

Example: an admin clicks **Save** to create a new student.

```
 Browser (app.js)
   │  POST /api/students
   │  Cookie: JSESSIONID=abc123          ← proves who you are
   │  X-CSRF-TOKEN: 9f1e...              ← proves the request came from our page
   │  { "name": "Rahim", "email": "...", "password": "..." }
   ▼
 ┌──────────────────────── Spring Security Filter Chain ─────────────────────────┐
 │ 1. SecurityContext filter: reads JSESSIONID, loads the logged-in user from     │
 │    the HTTP session                                                            │
 │ 2. CSRF filter: compares X-CSRF-TOKEN with the token saved in the session      │
 │       └─ mismatch/missing → 403 "Invalid or missing CSRF token"                │
 │ 3. Authorization filter: /api/students/** needs ROLE_ADMIN or ROLE_STUDENT     │
 │       ├─ not logged in → 401 "Authentication required"                         │
 │       └─ wrong role    → 403 "You do not have permission..."                   │
 └────────────────────────────────────────────────────────────────────────────────┘
   ▼
 StudentController.createStudent()
   │  @Valid checks CreateUserRequest  → if invalid: 400 with field errors
   │  calls userService.createUser(request, Role.STUDENT)
   ▼
 UserService.createUser()
   │  email already used?  → throw DuplicateEmailException → 409
   │  hash password with BCrypt
   │  role = STUDENT (decided by the controller, not the client)
   │  userRepository.save(user)
   ▼
 UserRepository (Spring Data JPA) → Hibernate → SQL INSERT → MySQL `users` table
   ▼
 Back up the chain: User entity → converted to UserResponse DTO (no password!)
   ▼
 HTTP 201 Created  { "id": 7, "name": "Rahim", "role": "STUDENT", ... }
```

If you can explain this diagram, you can explain the whole project.

---

## 4. Layer by Layer

### 4.1 Entity: `User` and `Role`

[`entity/User.java`](src/main/java/com/university/management/entity/User.java) is a normal Java class that **JPA maps to a database table**.

| Annotation | Meaning |
|---|---|
| `@Entity` | "This class is a database table." |
| `@Table(name = "users")` | Table name is `users` (`user` is a reserved word in SQL). |
| `@Id` + `@GeneratedValue(strategy = IDENTITY)` | `id` is the primary key, and MySQL auto-increments it. |
| `@Column(unique = true, nullable = false)` on `email` | The database itself refuses duplicate emails. This is a second line of defence after the service check. |
| `@Enumerated(EnumType.STRING)` on `role` | Stores `"ADMIN"` as text, not `0`. If the enum order changes later, existing data is still correct. |
| `@PrePersist onCreate()` | Runs automatically just before the first INSERT and sets `createdAt`. |

There is **one table for all three user types**. The `role` column tells them apart. This keeps the design simple because admins, teachers and students all have the same fields.

[`entity/Role.java`](src/main/java/com/university/management/entity/Role.java) is an enum: `ADMIN`, `TEACHER`, `STUDENT`. An enum means a role can never be a typo like `"techer"`.

Because `spring.jpa.hibernate.ddl-auto=update`, Hibernate creates or updates the `users` table on startup, so you never write `CREATE TABLE` yourself.

### 4.2 Repository: `UserRepository`

[`repository/UserRepository.java`](src/main/java/com/university/management/repository/UserRepository.java) is **an interface with no implementation code**. Spring Data JPA writes the class at runtime.

- `extends JpaRepository<User, Long>` gives you `save`, `findById`, `findAll`, `delete`, `existsById`, ... for free.
- The other methods are **derived queries**. Spring reads the method name and generates the SQL:

| Method | Generated SQL (roughly) |
|---|---|
| `findByEmail(email)` | `SELECT * FROM users WHERE email = ?` |
| `findByRole(role)` | `SELECT * FROM users WHERE role = ?` |
| `findByIdAndRole(id, role)` | `SELECT * FROM users WHERE id = ? AND role = ?` |
| `existsByEmail(email)` | `SELECT COUNT(*) > 0 FROM users WHERE email = ?` |

`findByIdAndRole` matters for security. `GET /api/teachers/5` only finds user 5 **if user 5 is a teacher**. Otherwise you can't read a student through the teacher endpoint. The integration test `StudentCrudIT` checks this.

`Optional<User>` forces the caller to deal with the "not found" case instead of getting a surprise `null`.

### 4.3 DTOs: request and response objects

A **DTO (Data Transfer Object)** describes the JSON that goes in or out. These are Java `record`s: short, immutable classes.

| DTO | Used for | Validation |
|---|---|---|
| [`CreateUserRequest`](src/main/java/com/university/management/dto/CreateUserRequest.java) | POST body | name required, valid email, password ≥ 8 characters |
| [`UpdateUserRequest`](src/main/java/com/university/management/dto/UpdateUserRequest.java) | PUT body | name and email required; **password optional** (blank = keep the old one) |
| [`LoginRequest`](src/main/java/com/university/management/dto/LoginRequest.java) | Login body | email and password required |
| [`UserResponse`](src/main/java/com/university/management/dto/UserResponse.java) | Every response | – |

**Why not just send and receive the `User` entity directly?**

1. **Security:** `UserResponse` has **no password field**, so the password hash can never leak to the browser.
2. **No role injection:** the request DTOs have **no `role` field**. A user can't send `"role": "ADMIN"` to promote themselves. The role always comes from which controller was called.
3. **Separation:** the database structure can change without breaking the API, and the reverse.

### 4.4 Service: `UserService`

[`service/UserService.java`](src/main/java/com/university/management/service/UserService.java) holds the **business rules**. One service serves all three roles. The controller passes in which `Role` it's working with, so there's no duplicated code.

| Method | What it does |
|---|---|
| `createUser(request, role)` | 1) reject duplicate email → `DuplicateEmailException`; 2) copy fields into a new `User`; 3) **hash the password** with BCrypt; 4) set the role given by the controller; 5) save; 6) return a `UserResponse`. |
| `getUsersByRole(role)` | All users with that role, converted to DTOs. |
| `getUserById(id, role)` | One user, or `ResourceNotFoundException` ("TEACHER not found with ID: 5"). |
| `updateUser(id, role, request)` | Find user → if the new email belongs to **someone else**, reject. Keeping your own email is fine → update fields → **only change the password if a non-blank one was sent**, and it must be ≥ 8 characters → save. |
| `deleteUser(id, role)` | Find (with the role check) and delete. |
| `convertToResponse(user)` | Entity → DTO (drops the password). |

**`@Transactional`** on the class means every public method runs in a database transaction. If anything throws halfway through, all changes in that method are rolled back. Read-only methods use `@Transactional(readOnly = true)`, which tells Hibernate it can skip change-tracking, so they run a little faster.

**Constructor injection:** `UserService` asks for `UserRepository` and `PasswordEncoder` in its constructor, and Spring passes them in automatically. This is also what makes unit testing easy: in tests we pass **fake (mock)** versions into the same constructor.

### 4.5 Controllers

Controllers are the "front desk". They receive HTTP, call the service and return an HTTP status plus JSON.

**`AdminController`, `TeacherController`, `StudentController`** are almost identical. Each one fixes its own role:

```java
@RestController                         // methods return JSON, not HTML pages
@RequestMapping("/api/students")        // base URL for this class
public class StudentController {

    @PostMapping                         // POST /api/students
    public ResponseEntity<UserResponse> createStudent(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)            // 201
                .body(userService.createUser(request, Role.STUDENT)); // role fixed here!
    }
    ...
}
```

- `@RequestBody` turns the JSON body into a `CreateUserRequest` object.
- `@Valid` runs the validation annotations. If any fail, the method **never runs** and a 400 comes back instead.
- `@PathVariable Long id` reads `{id}` from the URL.
- `ResponseEntity` lets us choose the status code: 201 Created, 200 OK, 204 No Content.

**[`AuthController`](src/main/java/com/university/management/controller/AuthController.java)** handles sessions:

| Endpoint | How it works |
|---|---|
| `GET /api/auth/csrf` | Spring passes in the `CsrfToken` for this session, and we return it as `{ "token": "..." }`. The frontend calls this first. |
| `POST /api/auth/login` | 1) `authenticationManager.authenticate(email, password)`: Spring Security loads the user through `CustomUserDetailsService` and compares the BCrypt hash. A wrong password throws `BadCredentialsException`, which becomes 401. 2) Put the result in a new `SecurityContext`. 3) `changeSessionId()` gives the browser a **new session ID** after login, which prevents **session fixation** attacks. 4) `securityContextRepository.saveContext(...)` stores the login in the HTTP session, so later requests are recognised by their `JSESSIONID` cookie. |
| `GET /api/auth/me` | Spring passes in the current `Authentication`. We look up the user and return their details. The frontend uses it to find out "am I logged in, and as what role?" |
| `POST /api/auth/logout` | `session.invalidate()` destroys the server-side session, so the cookie becomes useless. |

### 4.6 Error Handling: `GlobalExceptionHandler`

[`exception/GlobalExceptionHandler.java`](src/main/java/com/university/management/exception/GlobalExceptionHandler.java) is annotated `@RestControllerAdvice`. It catches exceptions thrown by **any** controller and turns them into clean JSON, so the client never sees a Java stack trace.

| Exception | Thrown by | HTTP | Example body |
|---|---|---|---|
| `ResourceNotFoundException` | service, ID not found | **404** | `{"status":404,"message":"STUDENT not found with ID: 9"}` |
| `DuplicateEmailException` | service, email taken | **409** | `{"status":409,"message":"Email already exists: a@b.com"}` |
| `MethodArgumentNotValidException` | Spring, when `@Valid` fails | **400** | `{"status":400,"message":"Validation failed","errors":{"email":"Invalid email address"}}` |
| `AuthenticationException` | login with wrong credentials | **401** | `{"status":401,"message":"Invalid email or password"}` |
| `IllegalArgumentException` | service, short password on update | **400** | `{"status":400,"message":"Password must be at least 8 characters"}` |

The login error deliberately says "Invalid email **or** password" and doesn't reveal which one was wrong. Otherwise an attacker could use it to discover which emails are registered.

Our custom exceptions extend `RuntimeException` (unchecked), so they don't need to be declared in every method signature.

### 4.7 Startup Data: `DataInitializer`

**Chicken-and-egg problem:** only admins can create users, but on a fresh database there are no admins.

[`config/DataInitializer.java`](src/main/java/com/university/management/config/DataInitializer.java) implements `CommandLineRunner`, so Spring runs its `run()` method **once, right after the app starts**. If no user with `app.admin.email` exists, it creates one with a hashed password. Because it checks first, restarting the app never creates duplicates.

Default credentials (from `.env` / `application.properties`):

```
Email:    admin@university.edu
Password: Admin@123
```

---

## 5. Security in Depth

Everything is configured in [`config/SecurityConfig.java`](src/main/java/com/university/management/config/SecurityConfig.java). It defines four beans:

### 5.1 `PasswordEncoder` → BCrypt

```java
return new BCryptPasswordEncoder();
```

- Passwords are **never stored as plain text**. `"Admin@123"` is stored as something like `$2a$10$N9qo8uLOickgx2ZMRZoMye...`.
- BCrypt is **one-way**: a hash can't be turned back into the password. At login, BCrypt hashes the typed password again and compares the two hashes.
- It adds a random **salt**, so two users with the same password get different hashes. It is also **deliberately slow**, which makes brute-force guessing expensive.

### 5.2 `AuthenticationManager`

This is Spring Security's "check these credentials" engine. Spring Boot builds it automatically from two beans:
- our `CustomUserDetailsService`, which says how to **find** a user, and
- our `PasswordEncoder`, which says how to **check** the password.

We only expose it as a bean so `AuthController` can call it.

### 5.3 `CustomUserDetailsService`

[`service/CustomUserDetailsService.java`](src/main/java/com/university/management/service/CustomUserDetailsService.java) is the bridge between **our** `User` table and **Spring Security's** idea of a user:

```java
return org.springframework.security.core.userdetails.User
        .withUsername(user.getEmail())       // we log in by email
        .password(user.getPassword())        // the BCrypt hash
        .roles(user.getRole().name())        // "TEACHER" → authority "ROLE_TEACHER"
        .build();
```

`.roles("TEACHER")` automatically adds the `ROLE_` prefix. That's why `hasRole("TEACHER")` in the rules below matches.

### 5.4 `SecurityContextRepository` → `HttpSessionSecurityContextRepository`

This decides **where the logged-in state is remembered**. We use the **HTTP session**:

1. At login, the server stores "this session belongs to admin@university.edu, ROLE_ADMIN" in its memory.
2. The browser gets a cookie: `JSESSIONID=abc123`.
3. On every later request, the browser sends the cookie automatically, and the server looks up the session.

This is **stateful, session-based authentication**, the classic approach. The alternative is **stateless JWT tokens**, which suit mobile apps and microservices. Sessions are simpler and fit a website that serves its own frontend.

### 5.5 The `SecurityFilterChain`: the rules

**CSRF protection**

```java
.csrf(csrf -> csrf
        .csrfTokenRepository(new HttpSessionCsrfTokenRepository())
        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
```

*What is CSRF (Cross-Site Request Forgery)?* You're logged in to our site in one tab. In another tab you visit `evil.com`, which secretly submits `POST /api/students/5 DELETE` to our server. Your browser **automatically attaches your session cookie**, so without protection the server would obey.

*How the defence works:*
1. The server generates a random **CSRF token** and stores it in your session.
2. Our own page fetches it from `GET /api/auth/csrf`.
3. Every POST/PUT/DELETE must send it in the `X-CSRF-TOKEN` **header**.
4. `evil.com` can't read the token (browsers block cross-site reads), so its forged requests are rejected with **403**.

GET requests don't need the token because they should never change data.

**Authorization rules (checked top to bottom; the first match wins):**

| URL | Who may access |
|---|---|
| `/`, `/index.html`, `/css/**`, `/js/**`, `/favicon.ico` | Everyone (the login page must load before you log in) |
| `/api/auth/csrf`, `/api/auth/login`, `/actuator/health`, `/error` | Everyone |
| `/api/admins/**` | `ADMIN` only |
| `/api/teachers/**` | `ADMIN` or `TEACHER` |
| `/api/students/**` | `ADMIN` or `STUDENT` |
| `/api/auth/logout`, everything else | Any logged-in user |

**Disabled defaults:** `formLogin`, `httpBasic` and `logout` are turned off because we provide our own JSON login and logout endpoints instead of Spring's built-in HTML login page.

**Custom JSON errors:**
- `authenticationEntryPoint` → **401** `{"message":"Authentication required"}`: you're **not logged in**.
- `accessDeniedHandler` → **403**: you're logged in but **not allowed**. It also reports a missing or invalid CSRF token with a specific message.

> **401 vs 403 (a common exam question):** 401 Unauthorized means "I don't know who you are." 403 Forbidden means "I know who you are, and you're not allowed."

---

## 6. The Frontend

A **single-page application** with no framework, in [`src/main/resources/static/`](src/main/resources/static/). Spring Boot serves anything in `static/` automatically, so `http://localhost:8081/` returns `index.html`.

**[`index.html`](src/main/resources/static/index.html)** contains three hidden or visible sections: the login view, the dashboard view (tabs + table) and a modal form for create and edit. JavaScript shows or hides them by toggling the `hidden` CSS class.

**[`js/app.js`](src/main/resources/static/js/app.js)**, step by step:

1. **Startup (`init`)**: fetches a CSRF token, then calls `GET /api/auth/me`. If that returns 200, the user is still logged in (for example after a page refresh), so it shows the dashboard. Otherwise it shows the login form.
2. **`api(method, url, body)` helper**: wraps `fetch()`. It adds the `X-CSRF-TOKEN` header to every non-GET request, parses JSON, and jumps back to the login screen on any 401.
3. **Login**: posts email and password, then calls `/me` to get the name and role.
4. **Tabs by role**: `ROLE_ACCESS` mirrors `SecurityConfig`: an ADMIN sees Admins/Teachers/Students tabs, a TEACHER sees Teachers, a STUDENT sees Students. *Hiding tabs is only for convenience. The real protection is on the server.* Even if someone edits the JavaScript, the server still returns 403.
5. **Table**: `GET /api/{resource}` and builds rows with `textContent`, **not** `innerHTML`, so a name like `<script>…</script>` is shown as text and can't run (this prevents **XSS**, cross-site scripting).
6. **Create/Edit form**: POST for new users, PUT for existing ones. Password is required on create and optional on edit.
7. **Delete**: asks `confirm()` first. The delete button is disabled for your own account.
8. **Logout**: posts to `/logout`, then fetches a **new** CSRF token, because the old one died with the session.

---

## 7. Configuration and Environment Variables

[`application.properties`](src/main/resources/application.properties) uses the syntax `${ENV_VAR:default}`: "use the environment variable if it is set, otherwise use the default."

| Property | Env var | Default (local run) | Meaning |
|---|---|---|---|
| `spring.datasource.url` | `DB_URL` | `jdbc:mysql://localhost:3306/university_db?createDatabaseIfNotExist=true...` | Where the database is |
| `spring.datasource.username` | `DB_USERNAME` | `root` | DB user |
| `spring.datasource.password` | `DB_PASSWORD` | *(empty)* | DB password |
| `spring.jpa.hibernate.ddl-auto` | – | `update` | Create or alter tables to match the entities |
| `spring.jpa.show-sql` | – | `true` | Print SQL in the console (handy for learning) |
| `spring.jpa.open-in-view` | – | `false` | Close DB connections when the service finishes (best practice) |
| `app.admin.email` / `.password` | `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `admin@university.edu` / `Admin@123` | The seeded admin |
| `management.endpoints.web.exposure.include` | – | `health` | Only expose `/actuator/health` |
| `server.port` | `SERVER_PORT` | `8081` | HTTP port |

**Docker Compose** reads [`.env`](.env) and passes these values to both containers. This way, **the same jar** runs locally and in Docker. Only the environment changes.

---

## 8. Running the Project

### Option A: Docker (easiest, no MySQL install needed)

```bash
docker compose up --build
```

- [`Dockerfile`](Dockerfile) is a **multi-stage build**. Stage 1 uses a Maven + JDK image to compile and package the jar. Stage 2 copies only the jar into a small JRE image, so the final image has no compiler or source code in it.
- [`compose.yaml`](compose.yaml) starts two containers:
  - `db`: MySQL 8.4 with a **healthcheck** (`mysqladmin ping`) and a named volume `mysql_data`, so data survives restarts.
  - `app`: waits until the DB is **healthy** (`depends_on: condition: service_healthy`) and connects using the hostname `db`. Inside Docker, containers reach each other by service name.
- Open **http://localhost:8081** (host port 8081 → container port 8080). MySQL is reachable from your PC on port **3307**.
- Stop: `docker compose down`. Stop **and delete the data**: `docker compose down -v`.

### Option B: Run locally

1. Have MySQL running on `localhost:3306`. Update the username and password via env vars, or edit the defaults.
2. Run:
   ```bash
   ./mvnw spring-boot:run        # Linux/macOS/Git Bash
   mvnw.cmd spring-boot:run      # Windows CMD/PowerShell
   ```
3. Open **http://localhost:8081** and log in with `admin@university.edu` / `Admin@123`.

---

## 9. API Reference

All bodies are JSON. Every POST/PUT/DELETE needs the `X-CSRF-TOKEN` header and the session cookie.

| Method | URL | Who | Body | Success |
|---|---|---|---|---|
| GET | `/api/auth/csrf` | anyone | – | 200 `{token}` |
| POST | `/api/auth/login` | anyone | `{email, password}` | 200 `{message, email, role}` |
| GET | `/api/auth/me` | logged in | – | 200 `UserResponse` |
| POST | `/api/auth/logout` | logged in | – | 200 |
| GET | `/api/admins` | ADMIN | – | 200 list |
| GET | `/api/admins/{id}` | ADMIN | – | 200 |
| POST | `/api/admins` | ADMIN | `CreateUserRequest` | 201 |
| PUT | `/api/admins/{id}` | ADMIN | `UpdateUserRequest` | 200 |
| DELETE | `/api/admins/{id}` | ADMIN | – | 204 |
| *same 5* | `/api/teachers...` | ADMIN, TEACHER | | |
| *same 5* | `/api/students...` | ADMIN, STUDENT | | |
| GET | `/actuator/health` | anyone | – | 200 `{"status":"UP"}` |

Example `CreateUserRequest`:

```json
{ "name": "Rahim", "email": "rahim@uni.edu", "password": "Student@123", "department": "CSE", "phone": "01900000000" }
```

---

## 10. Testing

### 10.1 Unit test vs integration test

| | **Unit test** | **Integration test** |
|---|---|---|
| Tests | **One class** on its own | **Many parts working together** |
| Dependencies | Replaced by **mocks** (fakes) | Real: Spring, Security, JPA, database |
| Database | None | H2 in-memory database |
| Speed | Milliseconds | Seconds (Spring has to start) |
| Finds | Logic bugs in that class | Wiring bugs, config mistakes, security holes |
| Naming here | `*Test.java` | `*IT.java` |
| Run by | Maven **Surefire** plugin (`mvn test`) | Maven **Failsafe** plugin (`mvn verify`) |

Every test follows the **Arrange → Act → Assert** pattern: set up the data, call the method, check the result.

### 10.2 Key tools used

- **JUnit 5:** `@Test` marks a test method, `assertEquals` / `assertThrows` / `assertTrue` check results, and `@DisplayName` gives a readable name in reports.
- **Mockito** (unit tests):
  - `@Mock UserRepository userRepository` is a fake repository that returns whatever we tell it.
  - `when(repo.existsByEmail("x")).thenReturn(true)` scripts an answer.
  - `@InjectMocks UserService userService` creates the real service with the mocks passed into its constructor.
  - `verify(repo, never()).save(any())` checks that a method was **not** called.
  - `ArgumentCaptor` grabs the object passed to `save()` so we can inspect it.
- **MockMvc** (integration tests): sends fake HTTP requests through the **real** security filters and controllers without opening a network port. `.session(session)` acts like a browser cookie, and `jsonPath("$.role").value("ADMIN")` checks the JSON response.
- **H2** + **`@ActiveProfiles("test")`**: loads [`application-test.properties`](src/test/resources/application-test.properties), which swaps MySQL for an in-memory H2 database in MySQL-compatibility mode. Tests need **no installed database**, which is what makes them work in GitHub Actions.

### 10.3 The 6 unit tests

**[`UserServiceTest`](src/test/java/com/university/management/service/UserServiceTest.java)** (mocks: `UserRepository`, `PasswordEncoder`)

| # | Test | What it proves |
|---|---|---|
| 1 | `createUser_hashesPasswordAndAssignsRole` | The saved user's password is the **hashed** value (captured with `ArgumentCaptor`), and the role is the one the controller passed in. |
| 2 | `createUser_duplicateEmail_throwsException` | An existing email throws `DuplicateEmailException` with the right message, and `save()` is **never** called. |
| 3 | `updateUser_blankPassword_keepsOldPassword` | A blank password on update leaves the old hash untouched, and the encoder is never called. Other fields do update. |
| 4 | `updateUser_shortPassword_throwsException` | A new password under 8 characters throws `IllegalArgumentException` and nothing is saved. |

**[`CustomUserDetailsServiceTest`](src/test/java/com/university/management/service/CustomUserDetailsServiceTest.java)** (mock: `UserRepository`)

| # | Test | What it proves |
|---|---|---|
| 5 | `loadUserByUsername_existingUser_returnsUserDetailsWithRole` | Our `User` becomes Spring Security's `UserDetails` with username = email, password = hash and authority **`ROLE_TEACHER`** (the prefix `hasRole()` depends on). |
| 6 | `loadUserByUsername_unknownEmail_throwsException` | An unknown email throws `UsernameNotFoundException`. |

### 10.4 The 3 integration tests

All three extend [`IntegrationTestSupport`](src/test/java/com/university/management/integration/IntegrationTestSupport.java). It starts the whole app (`@SpringBootTest`), provides `MockMvc`, and has a `loginAs(email, password)` helper that performs the **real** CSRF + login flow, exactly like `app.js`. The seeded admin (`admin@test.edu`) is created by the real `DataInitializer` when the app starts.

| # | Test class | Scenario | Verifies |
|---|---|---|---|
| 1 | [`AuthenticationFlowIT`](src/test/java/com/university/management/integration/AuthenticationFlowIT.java) | Wrong password → correct login → `/me` → logout → `/me` again | 401 "Invalid email or password"; 200 with role ADMIN; `/me` returns the admin and **no password field**; logout 200; afterwards 401 "Authentication required" (the session really is destroyed). |
| 2 | [`StudentCrudIT`](src/test/java/com/university/management/integration/StudentCrudIT.java) | Admin performs full CRUD on a student | Create 201 → **reads the row directly from the database** and confirms role STUDENT and a BCrypt-hashed password; duplicate 409; invalid body 400 with per-field messages; read 200; update 200 with the password unchanged; the student is **404 through `/api/teachers/{id}`**; delete 204 → row gone → 404. |
| 3 | [`RoleBasedAccessIT`](src/test/java/com/university/management/integration/RoleBasedAccessIT.java) | Anonymous user, then a student, then an admin without a CSRF token | Anonymous → 401, `/actuator/health` still public; admin creates a student, the student logs in; student → `/api/students` 200, `/api/teachers` 403, `/api/admins` 403; admin POST **without** `X-CSRF-TOKEN` → 403 "Invalid or missing CSRF token". |

### 10.5 Running the tests

```bash
# Only the 6 unit tests
./mvnw test                     # Windows: mvnw.cmd test

# Unit tests + 3 integration tests + build the jar
./mvnw verify                   # Windows: mvnw.cmd verify

# Only the integration tests
./mvnw verify -DskipUTs=true

# One single test class
./mvnw test -Dtest=UserServiceTest
```

Expected output:

```
[INFO] Tests run: 2, ... -- in ...CustomUserDetailsServiceTest
[INFO] Tests run: 4, ... -- in ...UserServiceTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0      ← unit (Surefire)
...
[INFO] Tests run: 1, ... -- in ...AuthenticationFlowIT
[INFO] Tests run: 1, ... -- in ...RoleBasedAccessIT
[INFO] Tests run: 1, ... -- in ...StudentCrudIT
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0      ← integration (Failsafe)
[INFO] BUILD SUCCESS
```

Detailed reports are written to `target/surefire-reports/` (unit) and `target/failsafe-reports/` (integration). In IntelliJ you can also right-click a test class → **Run**.

**How the build separates them** (in [`pom.xml`](pom.xml)):
- `maven-surefire-plugin` runs classes named `*Test` in the `test` phase. Its `skipTests` is linked to the `skipUTs` property, so `-DskipUTs=true` skips just the unit tests.
- `maven-failsafe-plugin` runs classes named `*IT` in the `integration-test` and `verify` phases, after the jar has been packaged.
- `h2` is a `test`-scope dependency, so it is **not** included in the production jar.

---

## 11. Continuous Integration with GitHub Actions

**Continuous Integration (CI)** means every time code is pushed, a server automatically builds it and runs all the tests. Broken code gets caught within minutes instead of on demo day.

The pipeline is defined in [`.github/workflows/ci.yml`](.github/workflows/ci.yml).

### 11.1 When it runs

```yaml
on:
  push:          { branches: [ main, master ] }   # every push
  pull_request:  { branches: [ main, master ] }   # every PR
  workflow_dispatch:                              # "Run workflow" button
```

### 11.2 What it does

```
push ─▶ ┌──────────────────┐   passed   ┌───────────────────────┐
        │  Unit Tests      │ ─────────▶ │  Integration Tests    │
        │  ./mvnw test     │  (needs:)  │  ./mvnw verify        │
        │  6 tests         │            │    -DskipUTs=true     │
        └──────────────────┘            │  3 tests + build jar  │
              │                         └───────────────────────┘
              ▼                                   ▼
     artifact: unit-test-reports     artifacts: integration-test-reports,
                                                university-management-jar
```

Each **job** runs on a fresh Ubuntu virtual machine and goes through these **steps**:

| Step | Why |
|---|---|
| `actions/checkout@v4` | Download your repository's code onto the VM. |
| `actions/setup-java@v4` (Temurin 21, `cache: maven`) | Install Java 21. The cache stores downloaded libraries so later runs are faster. |
| `chmod +x mvnw` | Files committed from Windows lose the Linux "executable" permission, and this restores it. |
| `./mvnw -B test` / `verify -DskipUTs=true` | Run the tests. `-B` (batch mode) gives clean log output. |
| `actions/upload-artifact@v4` with `if: always()` | Save the test reports, **even when tests fail**, so you can download them and see why. |

`needs: unit-tests` makes the integration job wait for the unit job and **skip if it fails**. The fast tests act as a gate in front of the slow ones.

**No MySQL is needed in CI** because the integration tests use H2.

### 11.3 How to use it

1. **The `.github` folder must be at the root of your Git repository.** It currently sits next to `pom.xml`, inside the inner `university-management/` folder. That's correct if you push **this inner folder** as the repository.
   *If you push the outer folder instead*, move `.github/` up one level and add this to both jobs:
   ```yaml
   defaults:
     run:
       working-directory: university-management
   ```
   and change the artifact paths to `university-management/target/...`.
2. Push:
   ```bash
   git init
   git add .
   git commit -m "Add tests and CI pipeline"
   git branch -M main
   git remote add origin https://github.com/<your-username>/<repo>.git
   git push -u origin main
   ```
3. On GitHub, open the **Actions** tab and click the latest **CI** run to watch both jobs. Green ✓ means all tests passed. Click a job to see the Maven log. Downloadable reports and the jar appear under **Artifacts** at the bottom of the run page.
4. Optional: add a status badge to the top of this README:
   ```markdown
   ![CI](https://github.com/<your-username>/<repo>/actions/workflows/ci.yml/badge.svg)
   ```

---

## 12. Likely Questions and Answers

**Q: Why is there a separate DTO instead of returning the entity?**
So the password hash is never sent to clients, so clients can't set their own `role`, and so the API shape is independent of the database shape.

**Q: Where is the role decided when creating a user?**
In the controller (`Role.STUDENT` inside `StudentController`), never from the request body. This prevents privilege escalation.

**Q: How does the server know who I am on the next request?**
After login, the authentication is saved in the HTTP session. The browser sends the `JSESSIONID` cookie on every request, and Spring Security loads the session and restores the user.

**Q: Why call `changeSessionId()` at login?**
To prevent session fixation. An attacker who planted a session ID before you logged in can't use it afterwards, because the ID changes at login.

**Q: Why is CSRF protection needed if we already have login?**
Login proves the *browser* is authenticated. CSRF protection proves the *request came from our own page*. The cookie is sent automatically even on requests triggered by a malicious site, and the token isn't.

**Q: 401 vs 403?**
401: not logged in. 403: logged in but not allowed (wrong role or missing CSRF token).

**Q: Why BCrypt and not SHA-256?**
BCrypt is salted and intentionally slow, which makes brute-force and rainbow-table attacks impractical. SHA-256 is designed to be fast, which is bad for passwords.

**Q: What does `@Transactional` give us?**
All-or-nothing database changes per service method. If an error happens midway, everything rolls back.

**Q: Why mocks in unit tests?**
To test *only* `UserService`'s logic, quickly and repeatably, with no database. We script exactly what the repository "returns" and check how the service reacts.

**Q: Why H2 for integration tests instead of MySQL?**
It runs in memory inside the test JVM, so tests need no installed database and work identically on any laptop and on GitHub Actions. MySQL mode keeps the SQL compatible.

**Q: Why are tests split into `*Test` and `*IT`?**
Different Maven plugins run them at different phases. Fast unit tests run first and act as a gate. Slower integration tests run after the jar is built. CI shows them as two separate jobs.

**Q: What happens on a fresh database?**
`DataInitializer` creates the first admin on startup, which solves the "no admin to create admins" problem.

**Q: What is the Dockerfile's multi-stage build for?**
The heavy Maven+JDK image is used only to compile. The final image contains just the JRE and the jar, so it is smaller and more secure.

---

## 13. Known Limitations and Possible Improvements

Being upfront about these is a good sign in a presentation:

- **Teachers can manage all teachers, and students can manage all students.** The rules check the URL prefix, not the HTTP method or the record's owner, so a teacher can create or delete other teachers. A stricter version would use `requestMatchers(HttpMethod.GET, "/api/teachers/**")` for read-only access, or method-level security (`@PreAuthorize`).
- **An admin can delete their own account through the API.** The frontend disables that button, but the backend doesn't check. If the last admin is deleted, `DataInitializer` recreates the default admin on the next restart.
- **`.env` contains passwords.** It is ignored by Docker (`.dockerignore`) but **not** by Git (`.gitignore`). For a real project, add `.env` to `.gitignore` and commit a `.env.example` with placeholder values instead.
- **`ddl-auto=update`** is convenient for development. Production systems usually use versioned migrations (Flyway or Liquibase).
- **No pagination** on list endpoints. Fine for a class project, but slow with thousands of users.
