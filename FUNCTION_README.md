# Function Reference: Tests and CI Pipeline

This file explains **every function** in the project's test code and every step of the GitHub Actions workflow: what it does, how it does it, and why it is there.

| Area | File | What it contains |
|---|---|---|
| Unit tests | [`UserServiceTest.java`](src/test/java/com/university/management/service/UserServiceTest.java) | Unit tests 1–4 + `buildUser` helper |
| Unit tests | [`CustomUserDetailsServiceTest.java`](src/test/java/com/university/management/service/CustomUserDetailsServiceTest.java) | Unit tests 5–6 |
| Integration tests | [`IntegrationTestSupport.java`](src/test/java/com/university/management/integration/IntegrationTestSupport.java) | Shared base class and helper functions |
| Integration tests | [`AuthenticationFlowIT.java`](src/test/java/com/university/management/integration/AuthenticationFlowIT.java) | Integration test 1: login / me / logout |
| Integration tests | [`StudentCrudIT.java`](src/test/java/com/university/management/integration/StudentCrudIT.java) | Integration test 2: student CRUD |
| Integration tests | [`RoleBasedAccessIT.java`](src/test/java/com/university/management/integration/RoleBasedAccessIT.java) | Integration test 3: security rules |
| Test config | [`application-test.properties`](src/test/resources/application-test.properties) | H2 database and seeded admin for tests |
| CI | [`.github/workflows/ci.yml`](.github/workflows/ci.yml) | Two-job pipeline: unit tests, then integration tests |

---

## 1. Unit tests vs. integration tests

| | Unit tests (`*Test.java`) | Integration tests (`*IT.java`) |
|---|---|---|
| What runs | One class (e.g. `UserService`) in isolation | The **whole** Spring Boot application |
| Dependencies | Faked with **Mockito** mocks | Real beans: controllers, security, JPA, BCrypt |
| Database | None | In-memory **H2** database |
| Maven plugin | Surefire, runs on `mvn test` | Failsafe, runs on `mvn verify` |
| Speed | Milliseconds | Seconds (Spring has to start) |

The Maven naming split is set up in [`pom.xml`](pom.xml): Surefire picks up `*Test.java`, Failsafe picks up `*IT.java`. The property `skipUTs` (default `false`) lets the CI integration job skip the unit tests so they are not run twice.

---

## 2. Unit tests

Both unit test classes use the same Mockito setup:

```java
@ExtendWith(MockitoExtension.class)   // lets JUnit 5 process @Mock / @InjectMocks
@Mock          private UserRepository userRepository;   // fake object; we script its answers
@InjectMocks   private UserService userService;         // real object, built with the mocks passed to its constructor
```

- **`@Mock`** creates a fake object. Every method returns an empty default (`null`, `false`, `Optional.empty()`) unless a test scripts it with `when(...).thenReturn(...)`.
- **`@InjectMocks`** creates a *real* instance of the class under test and passes the mocks into its constructor. Only the logic inside that class is tested; there is no Spring and no database.
- Each test follows the **Arrange / Act / Assert** pattern: set up inputs and mock answers, call the method, check the result.

### 2.1 `UserServiceTest`

Mocks: `UserRepository`, `PasswordEncoder`. Real object: `UserService`.

#### `createUser_hashesPasswordAndAssignsRole()` (Unit test 1)

**Checks:** `createUser` hashes the password before saving and assigns the role the controller passes in.

**How it works:**
1. **Arrange**
   - Builds a `CreateUserRequest` for Alice with the plain password `"plainPassword"`.
   - `existsByEmail("alice@uni.edu")` → `false` (the email is free).
   - `passwordEncoder.encode("plainPassword")` → `"HASHED_PASSWORD"`.
   - `userRepository.save(any(User.class))` uses `thenAnswer` to imitate the database: it takes the `User` that was passed in, gives it ID `1L`, and returns it.
2. **Act:** calls `userService.createUser(request, Role.STUDENT)`.
3. **Assert (the response):** `id == 1`, `email == "alice@uni.edu"`, `role == STUDENT`.
4. **Assert (what reached the database):** an `ArgumentCaptor<User>` captures the exact object passed to `save(...)`. The test checks its password is `"HASHED_PASSWORD"`, which proves plain text is never stored.

**Why it matters:** this is the main security guarantee for stored passwords.

#### `createUser_duplicateEmail_throwsException()` (Unit test 2)

**Checks:** a duplicate email is rejected and nothing is saved.

**How it works:**
1. `existsByEmail("taken@uni.edu")` → `true`.
2. `assertThrows(DuplicateEmailException.class, ...)` runs `createUser(request, Role.TEACHER)` and requires that exact exception type.
3. The exception message must be `"Email already exists: taken@uni.edu"`.
4. `verify(userRepository, never()).save(any())` confirms the service stopped **before** writing anything.

**Why it matters:** in the running app, this exception is turned into an HTTP **409 Conflict** (integration test 2 checks that).

#### `updateUser_blankPassword_keepsOldPassword()` (Unit test 3)

**Checks:** when an update request has a blank password, the old password hash is kept.

**How it works:**
1. `buildUser(5L, "old@uni.edu", "OLD_HASH", Role.TEACHER)` creates the existing teacher.
2. Mocks:
   - `findByIdAndRole(5L, TEACHER)` → the existing user (found, with the right role).
   - `findByEmail("new@uni.edu")` → empty (the new email is not taken).
   - `save(...)` returns whatever it receives (`invocation.getArgument(0)`).
3. Calls `updateUser(5L, Role.TEACHER, request)` with name `"New Name"`, email `"new@uni.edu"`, password `""`, department `"EEE"`.
4. Asserts the response has the new name, email and department.
5. Asserts `existing.getPassword()` is still `"OLD_HASH"`, and `verify(passwordEncoder, never()).encode(anyString())` proves the encoder was never called.

**Why it matters:** an admin can edit a user's details without having to know or re-enter their password.

#### `updateUser_shortPassword_throwsException()` (Unit test 4)

**Checks:** a new password shorter than 8 characters is rejected.

**How it works:**
1. Existing student Carol (ID `7`) is returned by `findByIdAndRole(7L, STUDENT)`.
2. `findByEmail("carol@uni.edu")` returns Carol herself. Keeping her own email is not a duplicate, so the email check passes and the code reaches the password check.
3. The request has password `"short"` (5 characters).
4. `assertThrows(IllegalArgumentException.class, ...)` with message `"Password must be at least 8 characters"`.
5. `verify(userRepository, never()).save(any())` confirms no save happened.

**Why it matters:** on update, the password is optional, so the "at least 8 characters" rule can't be a plain bean-validation annotation. It lives in the service, and this test covers it.

#### `buildUser(Long id, String email, String passwordHash, Role role)` (helper)

A private factory method that creates a `User` with the given ID, email, password hash and role, plus the fixed name `"Existing User"`. Unit tests 3 and 4 use it to build the "already in the database" user without repeating setter calls.

### 2.2 `CustomUserDetailsServiceTest`

Mock: `UserRepository`. Real object: `CustomUserDetailsService`, the class Spring Security calls during login to look up a user by email.

#### `loadUserByUsername_existingUser_returnsUserDetailsWithRole()` (Unit test 5)

**Checks:** the project's `User` entity is converted correctly into Spring Security's `UserDetails`.

**How it works:**
1. Builds a `User` with email `teacher@uni.edu`, password `"HASHED"` and role `TEACHER`.
2. `findByEmail("teacher@uni.edu")` → `Optional.of(user)`.
3. Calls `loadUserByUsername("teacher@uni.edu")`.
4. Asserts:
   - `getUsername()` is the email (email is the login name).
   - `getPassword()` is the stored hash, unchanged.
   - There is exactly **one** authority, and it is `"ROLE_TEACHER"`.

**Why it matters:** `SecurityConfig` uses `hasRole("TEACHER")`, which looks for the authority `ROLE_TEACHER`. If the `ROLE_` prefix were missing, every role check would fail with 403.

#### `loadUserByUsername_unknownEmail_throwsException()` (Unit test 6)

**Checks:** an unknown email raises the exception Spring Security expects.

**How it works:**
1. `findByEmail("ghost@uni.edu")` → `Optional.empty()`.
2. `assertThrows(UsernameNotFoundException.class, ...)`.
3. The message must be `"User not found: ghost@uni.edu"`.

**Why it matters:** Spring Security turns this exception into a failed login. Integration test 1 checks that a failed login returns **401**.

---

## 3. Integration tests

### 3.1 `IntegrationTestSupport` (abstract base class)

All three `*IT` classes `extend` this class, so they share its setup and helpers.

**Class annotations:**

| Annotation | Effect |
|---|---|
| `@SpringBootTest` | Starts the **whole** application: controllers, services, security, JPA and `DataInitializer`, as in production. |
| `@AutoConfigureMockMvc` | Provides `MockMvc`, which sends fake HTTP requests through the full Spring Security filter chain without opening a network port. |
| `@ActiveProfiles("test")` | Loads `application-test.properties`, which swaps MySQL for H2 (see [section 3.5](#35-application-testproperties)). |

**Fields:**
- `ADMIN_EMAIL = "admin@test.edu"` and `ADMIN_PASSWORD = "AdminTest@123"`: must match `app.admin.*` in the test properties, because `DataInitializer` creates this admin when the app starts.
- `mockMvc`: injected with `@Autowired`; used to perform every request.

#### `record Client(MockHttpSession session, String csrfToken)`

A small data holder that represents **one logged-in browser**: its session (standing in for the session cookie) and its CSRF token. `loginAs` returns it so tests can send authenticated requests with `admin.session()` and `admin.csrfToken()`.

#### `fetchCsrfToken(MockHttpSession session)`

**Does:** gets a CSRF token, the same way the frontend's `fetchCsrfToken()` does.

**How:**
1. Sends `GET /api/auth/csrf` with the given session.
2. Expects `200 OK`.
3. Reads the JSON body and pulls out `$.token` with `JsonPath.read(...)`.
4. Returns the token string.

The token is tied to the session, so later POST/PUT/DELETE requests must send **the same session** and the token in the `X-CSRF-TOKEN` header.

#### `loginAs(String email, String password)`

**Does:** performs the real login flow and returns a logged-in `Client`.

**How:**
1. Creates a new empty `MockHttpSession`.
2. Calls `fetchCsrfToken(session)`.
3. Sends `POST /api/auth/login` with that session, the `X-CSRF-TOKEN` header and a JSON body built by `loginJson(...)`.
4. Expects `200 OK`, so the test fails right away if login breaks.
5. Returns `new Client(session, csrfToken)`. The session now holds the authenticated user.

#### `loginJson(String email, String password)`

Returns the JSON request body for login, built with a Java text block and `.formatted(...)`:

```json
{"email": "<email>", "password": "<password>"}
```

#### `userJson(String name, String email, String password, String department)`

Returns the JSON body for creating or updating a student, teacher or admin. The phone number is always `01900000000`:

```json
{"name": "...", "email": "...", "password": "...", "department": "...", "phone": "01900000000"}
```

#### `uniqueEmail(String prefix)`

Returns `prefix + "." + System.nanoTime() + "@test.edu"`, for example `student.84731928374@test.edu`.

All integration tests share one Spring context and one H2 database during a run, so fixed emails could collide (a 409 from a user created by another test). A timestamp makes every email unique.

### 3.2 `AuthenticationFlowIT`

#### `loginMeLogout_fullLifecycle()` (Integration test 1)

**Checks:** the full session-based login lifecycle across `SecurityConfig`, `AuthController`, `AuthenticationManager`, `CustomUserDetailsService`, BCrypt, `UserRepository`, H2, the seeded admin and `GlobalExceptionHandler`.

**How (5 steps, one shared session):**

| # | Request | Expected | Proves |
|---|---|---|---|
| 0 | `fetchCsrfToken(session)` | token | A CSRF token is available before login |
| 1 | `POST /api/auth/login` with the **wrong** password | `401`, `message = "Invalid email or password"` | `BadCredentialsException` is mapped to a clean JSON 401 by `GlobalExceptionHandler` |
| 2 | `POST /api/auth/login` with the **correct** admin password | `200`, `message = "Login successful"`, `role = "ADMIN"` | BCrypt check works; the seeded admin exists |
| 3 | `GET /api/auth/me` | `200`, `email = admin@test.edu`, `role = ADMIN`, **no `password` field** | The session remembers the user, and the password hash is never sent to the client |
| 4 | `POST /api/auth/logout` | `200`, `message = "Logout successful"` | Logout endpoint works |
| 5 | `GET /api/auth/me` with the **old** session | `401`, `message = "Authentication required"` | The session was really invalidated; `SecurityConfig`'s entry point returns JSON, not an HTML login page |

### 3.3 `StudentCrudIT`

Also autowires the real `UserRepository` and `PasswordEncoder`, so the test can look directly at the database row.

#### `adminManagesStudent_endToEnd()` (Integration test 2)

**Checks:** full Create / Read / Update / Delete on students, from the HTTP request down to the database row and back.

**How:**
1. **Login:** `loginAs(ADMIN_EMAIL, ADMIN_PASSWORD)` and generate a unique email.
2. **Create:** `POST /api/students` (Rahim, CSE) → `201 Created`. Checks `email`, `role = "STUDENT"` and that `createdAt` exists. The new `id` is read from the JSON with `JsonPath`.
3. **Database check:** `userRepository.findById(id)`:
   - role is `Role.STUDENT`;
   - stored password is **not** `"Student@123"`;
   - `passwordEncoder.matches("Student@123", hash)` is `true`, so it is a valid BCrypt hash of the original.
4. **Duplicate email:** posting the same email again → `409 Conflict`.
5. **Invalid body:** blank name, `"not-an-email"`, password `"short"` → `400 Bad Request` with one error message per field:
   - `errors.name = "Name is required"`
   - `errors.email = "Invalid email address"`
   - `errors.password = "Password must be at least 8 characters"`
6. **Read one:** `GET /api/students/{id}` → `200`, name `"Rahim"`.
7. **Update:** `PUT /api/students/{id}` with name `"Rahim Uddin"`, department `"EEE"`, **blank password** → `200` with the new values. The database hash still matches `"Student@123"`, which repeats unit test 3 against the real database.
8. **Role isolation:** `GET /api/teachers/{id}` with the student's ID → `404`. Each endpoint only sees users of its own role.
9. **Delete:** `DELETE /api/students/{id}` → `204 No Content`. Then `userRepository.existsById(id)` is `false` and `GET /api/students/{id}` → `404`.

### 3.4 `RoleBasedAccessIT`

#### `securityRules_areEnforced()` (Integration test 3)

**Checks:** the three layers of protection in `SecurityConfig` in the running app: **401** when not logged in, **403** for the wrong role, **403** when the CSRF token is missing.

**How:**
1. **Anonymous user:**
   - `GET /api/students` with no session → `401`, `"Authentication required"`.
   - `GET /actuator/health` → `200` (public endpoint stays open).
2. **Setup:** the admin logs in and creates a student `Karim` through `POST /api/students` → `201`.
3. **Student logs in** with `loginAs(studentEmail, "Student@123")`.
4. **Allowed:** `GET /api/students` as the student → `200`.
5. **Forbidden by role:**
   - `GET /api/teachers` → `403`, `"You do not have permission to access this resource"`.
   - `GET /api/admins` → `403`.
6. **CSRF enforced:** the **admin** sends `POST /api/teachers` **without** the `X-CSRF-TOKEN` header → `403`, and the message starts with `"Invalid or missing CSRF token"`. Even the highest role is blocked without the token.

### 3.5 `application-test.properties`

Not a function, but every integration test depends on it. It is loaded only by `@ActiveProfiles("test")` and overrides `application.properties`:

| Setting | Value | Why |
|---|---|---|
| `spring.datasource.url` | `jdbc:h2:mem:university_test;MODE=MySQL;...` | In-memory H2 database that imitates MySQL. No real database server is needed, so CI can run it. `DB_CLOSE_DELAY=-1` keeps it alive for the whole run. |
| `spring.jpa.hibernate.ddl-auto` | `create-drop` | Builds the tables at startup and drops them at shutdown, so every run starts clean. |
| `spring.jpa.show-sql` | `false` | Keeps test logs readable. |
| `app.admin.email` / `app.admin.password` | `admin@test.edu` / `AdminTest@123` | The admin `DataInitializer` seeds; matches the `ADMIN_*` constants in `IntegrationTestSupport`. |

---

## 4. GitHub Actions pipeline (`.github/workflows/ci.yml`)

### 4.1 Triggers (`on:`)

| Trigger | When it runs |
|---|---|
| `push` to `main` / `master` | Every commit pushed to the main branch |
| `pull_request` to `main` / `master` | Every PR targeting the main branch, so bugs are caught before merging |
| `workflow_dispatch` | Manually, from the **Actions** tab ("Run workflow" button) |

### 4.2 Job 1: `unit-tests` ("Unit Tests")

Runs on a fresh `ubuntu-latest` virtual machine.

| Step | What it does |
|---|---|
| **Check out the code** (`actions/checkout@v4`) | Clones the repository into the VM. |
| **Set up Java 21** (`actions/setup-java@v4`) | Installs Eclipse Temurin JDK 21. `cache: maven` saves `~/.m2` between runs so dependencies aren't downloaded every time. |
| **Make the Maven wrapper executable** (`chmod +x mvnw`) | Files committed from Windows lose the Unix execute bit; without this step `./mvnw` fails with "Permission denied". |
| **Run unit tests** (`./mvnw -B test`) | Runs Maven up to the `test` phase, so **Surefire** runs the `*Test.java` classes. `-B` is batch mode (plain log output, no interactive prompts). |
| **Upload unit test reports** (`actions/upload-artifact@v4`) | Uploads `target/surefire-reports/` as the artifact `unit-test-reports`. `if: always()` makes it run **even when tests fail**, so you can download the reports and see why. |

### 4.3 Job 2: `integration-tests` ("Integration Tests")

`needs: unit-tests` means this job **only starts if job 1 passed**. If a unit test fails, the slower integration tests are skipped.

| Step | What it does |
|---|---|
| **Check out the code** | Same as job 1. Each job runs on a separate, fresh VM. |
| **Set up Java 21** | Same as job 1, with Maven caching. |
| **Make the Maven wrapper executable** | Same as job 1. |
| **Run integration tests and build the jar** (`./mvnw -B verify -DskipUTs=true`) | Runs Maven up to `verify`: compile → `test` → `package` (builds the jar) → `integration-test` → `verify`. **Failsafe** runs the `*IT.java` classes against H2 and fails the build if any fail. `-DskipUTs=true` sets the `pom.xml` property that skips Surefire, because unit tests already passed in job 1. |
| **Upload integration test reports** | Uploads `target/failsafe-reports/` as `integration-test-reports`, also with `if: always()`. |
| **Upload application jar** | Uploads `target/*.jar` as `university-management-jar`. It runs only if every step before it succeeded, so a jar is published only when all tests pass. |

### 4.4 Overall flow

```
push / pull request / manual run
        │
        ▼
┌──────────────────────┐   fail → stop, upload unit-test-reports
│ Job 1: Unit Tests    │
│ mvn test (Surefire)  │
└──────────┬───────────┘
           │ pass
           ▼
┌──────────────────────────────┐   fail → upload integration-test-reports
│ Job 2: Integration Tests     │
│ mvn verify -DskipUTs=true    │
│ (Failsafe + H2 + build jar)  │
└──────────┬───────────────────┘
           │ pass
           ▼
   Artifacts: integration-test-reports + university-management-jar
```

---

## 5. Running the tests locally

From the `university-management` folder (use `mvnw.cmd` on Windows, `./mvnw` on macOS/Linux):

| Command | Runs |
|---|---|
| `mvnw.cmd test` | Unit tests only (6 tests) |
| `mvnw.cmd verify` | Unit tests, then integration tests (3 tests), and builds the jar |
| `mvnw.cmd verify -DskipUTs=true` | Integration tests only (same as CI job 2) |
| `mvnw.cmd test -Dtest=UserServiceTest` | A single unit test class |
| `mvnw.cmd verify -DskipUTs=true -Dit.test=StudentCrudIT` | A single integration test class |

Reports are written to `target/surefire-reports/` (unit) and `target/failsafe-reports/` (integration).
