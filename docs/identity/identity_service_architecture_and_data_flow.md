# Identity Service: Architecture, Request-Response Flows, JPAs & DTOs

This document presents a comprehensive architectural visualization and data flow specification for the **Identity Service (`hu.bme.szoftarch.identity`)**, its interactions with **Clients** (via the API Gateway) and **Other Internal Services** (such as Learning Service and Social Service), as well as the complete mapping of **JPA Entities** and **DTO Records**.

---

## 1. High-Level Architecture & Interaction Topology

The following diagram illustrates the boundary between public client access (routed through Nginx and Spring Cloud Gateway), internal microservice-to-microservice communication (using direct internal calls protected by shared secrets), and the Zero-Trust token validation mechanism.

```mermaid
flowchart TB
    subgraph Clients["Clients & Edge Tier"]
        Browser["React 19 SPA / Web Client"]
        Mobile["Mobile / External REST Client"]
        Nginx["Nginx Reverse Proxy (:443)"]
        Gateway["Spring Cloud Gateway (:8080)<br/>CorrelationIdFilter & Edge Routing"]
    end

    subgraph Internal_Network["Internal Microservice Network (Zero-Trust)"]
        IdentityService["Identity Service (:8081)<br/>• Auth, Profile, Leaderboards<br/>• Points & Level Engine<br/>• RS256 Private Key Signing"]
        LearningService["Learning Service (:8082)<br/>• Lessons & Quizzes<br/>• Local RS256 JWT Verification"]
        SocialService["Social Service (:8083)<br/>• Friends, Duels, Leaderboard<br/>• Local RS256 JWT Verification"]
        AIService["AI Service (:8084)<br/>• Scenarios & Evaluation"]
    end

    subgraph Persistence["Identity Persistence Tier"]
        DB[(PostgreSQL :5432 / identity_db)]
        T_Users[("users")]
        T_Stats[("user_stats")]
        T_Tokens[("refresh_tokens")]
        T_Points[("point_transactions")]
        DB --- T_Users
        DB --- T_Stats
        DB --- T_Tokens
        DB --- T_Points
    end

    Browser -->|HTTPS| Nginx
    Mobile -->|HTTPS| Nginx
    Nginx --> Gateway

    Gateway -->|"Public /api/v1/auth/**<br/>Public /api/v1/users/**<br/>Public /api/v1/leaderboard/**"| IdentityService
    Gateway -->|"Public /api/v1/learning/**<br/>Bearer JWT"| LearningService
    Gateway -->|"Public /api/v1/friends/**<br/>Bearer JWT"| SocialService

    IdentityService -->|JPA / Hibernate / HikariCP| DB

    %% Service to Service
    LearningService -->|"POST /internal/v1/users/{userId}/points<br/>Header: X-Internal-Api-Key<br/>Payload: AddPointsRequest"| IdentityService
    SocialService -->|"POST /internal/v1/users/public-stats:batch<br/>Header: X-Internal-Api-Key<br/>Payload: BatchStatsRequest"| IdentityService

    %% Asymmetric Key Distribution
    IdentityService -.->|"Shares jwt-public.pem<br/>(Zero-Trust local validation)"| LearningService
    IdentityService -.->|"Shares jwt-public.pem<br/>(Zero-Trust local validation)"| SocialService
    IdentityService -.->|"Shares jwt-public.pem<br/>(Zero-Trust local validation)"| AIService
```

---

## 2. JPA Domain Model & Entity-Relationship Diagram (ERD)

The Identity Service domain consists of 4 main JPA entities, mapped directly to PostgreSQL tables with strict constraints, indexes, and an optimistic locking mechanism.

```mermaid
erDiagram
    User ||--|| UserStats : "maps 1:1 via userId (@MapsId)"
    User ||--o{ RefreshToken : "owns 1:N tokens"
    User ||--o{ PointTransaction : "owns 1:N point records"
    RefreshToken ||--o| RefreshToken : "replaced_by_id (RTR chain)"

    User {
        UUID id PK "UUIDv4, non-updatable"
        varchar email UK "255, unique, normalized lowercase"
        varchar username UK "30, unique, regex validated"
        varchar displayName "100, sanitized display name"
        varchar passwordHash "255, BCrypt hashed"
        varchar status "20, enum: ACTIVE, BLOCKED"
        timestamptz createdAt "UTC timestamp, default now()"
        timestamptz updatedAt "UTC timestamp"
    }

    UserStats {
        UUID userId PK,FK "MapsId from User.id"
        bigint totalPoints "Total accumulated XP (>= 0)"
        int level "Calculated level (>= 1)"
        bigint version "Optimistic lock (@Version)"
        timestamptz updatedAt "UTC timestamp"
    }

    RefreshToken {
        UUID id PK "UUIDv4"
        UUID userId FK "References users.id"
        varchar tokenHash UK "255, SHA-256 of raw token"
        timestamptz expiresAt "UTC, now + 7 days"
        timestamptz revokedAt "UTC, null if valid"
        UUID replacedById FK "Child token in rotation chain"
        varchar userAgentHash "255, SHA-256 of client User-Agent"
        timestamptz createdAt "UTC timestamp"
    }

    PointTransaction {
        UUID id PK "UUIDv4"
        UUID userId FK "References users.id"
        varchar sourceType "40, e.g. LESSON_COMPLETION"
        varchar sourceId "255, nullable reference ID"
        int points "Points awarded (> 0)"
        varchar idempotencyKey UK "255, unique constraint"
        timestamptz occurredAt "UTC timestamp"
    }
```

### Key JPA Architectural Highlights:
1. **Primary Key Sharing (`@MapsId`):** `UserStats` shares its primary key `userId` with `User.id` via `@OneToOne` and `@MapsId`. This guarantees $1:1$ cardinality and optimal join performance.
2. **Optimistic Locking (`@Version`):** `UserStats.version` prevents the **Lost Update** problem when concurrent point awards happen simultaneously.
3. **Immutability & Token Hash Storage:** Raw refresh tokens are never persisted in the database; only their **SHA-256 digest** (`tokenHash`) is stored.
4. **Token Family Linking (`replacedById`):** In Refresh Token Rotation (RTR), each rotated token points to its successor, forming an auditable directed graph.

---

## 3. Class Diagram: DTOs & JPA Entities Decoupling

The DTO layer strictly decouples public API contracts from internal database representations, using Java records for immutability.

```mermaid
classDiagram
    direction TB

    %% JPA Entities
    class User {
        -UUID id
        -String email
        -String username
        -String displayName
        -String passwordHash
        -UserStatus status
        -Instant createdAt
        -Instant updatedAt
        -UserStats stats
    }

    class UserStats {
        -UUID userId
        -User user
        -long totalPoints
        -int level
        -Long version
        -Instant updatedAt
    }

    class RefreshToken {
        -UUID id
        -User user
        -String tokenHash
        -Instant expiresAt
        -Instant revokedAt
        -UUID replacedById
        -String userAgentHash
        -Instant createdAt
        +isRevoked() boolean
        +isReplaced() boolean
        +isExpired() boolean
    }

    class PointTransaction {
        -UUID id
        -User user
        -String sourceType
        -String sourceId
        -int points
        -String idempotencyKey
        -Instant occurredAt
    }

    %% Client Auth DTOs
    class RegisterRequest {
        <<record>>
        +String email
        +String username
        +String displayName
        +String password
    }

    class LoginRequest {
        <<record>>
        +String loginIdentifier
        +String password
    }

    class AuthResponse {
        <<record>>
        +String accessToken
        +String tokenType
        +long expiresIn
        +UserSummaryDto user
    }

    class UserSummaryDto {
        <<record>>
        +UUID id
        +String email
        +String username
        +String displayName
        +Instant createdAt
    }

    %% Client Profile & Leaderboard DTOs
    class UserProfileResponse {
        <<record>>
        +UUID id
        +String email
        +String username
        +String displayName
        +long totalPoints
        +int level
        +long nextLevelPoints
        +Instant createdAt
    }

    class UpdateProfileRequest {
        <<record>>
        +String username
        +String displayName
    }

    class PublicProfileDto {
        <<record>>
        +UUID id
        +String username
        +String displayName
        +int level
        +long totalPoints
    }

    class LeaderboardResponse {
        <<record>>
        +List~LeaderboardEntryDto~ entries
        +LeaderboardEntryDto myRank
        +int page
        +int size
        +long totalElements
        +int totalPages
    }

    class LeaderboardEntryDto {
        <<record>>
        +long rank
        +UUID userId
        +String username
        +String displayName
        +int level
        +long totalPoints
    }

    %% Internal Service DTOs
    class AddPointsRequest {
        <<record>>
        +Integer points
        +String sourceType
        +String sourceId
        +String idempotencyKey
    }

    class PointTransactionResponse {
        <<record>>
        +UUID transactionId
        +UUID userId
        +int pointsAdded
        +long totalPoints
        +int level
        +long nextLevelPoints
        +boolean duplicate
        +Instant occurredAt
    }

    class BatchStatsRequest {
        <<record>>
        +List~UUID~ userIds
    }

    %% Error Handling DTO
    class ProblemDetails {
        <<record>>
        +String type
        +String title
        +int status
        +String detail
        +String instance
        +String code
        +String traceId
        +List~FieldErrorDto~ fieldErrors
    }

    class FieldErrorDto {
        <<record>>
        +String field
        +String message
    }

    %% Relationships
    User "1" *-- "1" UserStats : maps
    User "1" *-- "0..*" RefreshToken : generates
    User "1" *-- "0..*" PointTransaction : logs

    RegisterRequest ..> User : creates
    User ..> UserSummaryDto : projects to
    LoginRequest ..> User : authenticates
    RefreshToken ..> AuthResponse : issues session
    UserSummaryDto --* AuthResponse : contains

    User ..> UserProfileResponse : projects to
    UserStats ..> UserProfileResponse : projects to
    UpdateProfileRequest ..> User : updates

    User ..> PublicProfileDto : projects to
    UserStats ..> PublicProfileDto : projects to
    UserStats ..> LeaderboardEntryDto : aggregates to
    LeaderboardEntryDto --* LeaderboardResponse : contains

    AddPointsRequest ..> PointTransaction : records
    PointTransaction ..> PointTransactionResponse : returns
    BatchStatsRequest ..> PublicProfileDto : batch resolves
    FieldErrorDto --* ProblemDetails : details
```

---

## 4. Sequence Diagrams: Detailed Communication Flows

### Flow 1: Client Registration (`POST /api/v1/auth/register`)

```mermaid
sequenceDiagram
    autonumber
    actor Client as Browser / Client
    participant GW as API Gateway
    participant Ctrl as AuthController
    participant Svc as AuthService
    participant PassEnc as BCryptPasswordEncoder
    participant UserRepo as UserRepository
    participant DB as PostgreSQL

    Client->>GW: POST /api/v1/auth/register<br/>Payload: RegisterRequest
    GW->>Ctrl: Forward request with X-Correlation-Id
    Note over Ctrl: Jakarta Validation (@Valid):<br/>email valid, username pattern, password complexity
    Ctrl->>Svc: register(RegisterRequest)
    activate Svc
    Svc->>UserRepo: existsByEmail(email) / existsByUsername(username)
    UserRepo->>DB: SELECT count(*) WHERE email = ? / username = ?
    DB-->>UserRepo: 0 (Available)
    Svc->>PassEnc: encode(rawPassword)
    PassEnc-->>Svc: passwordHash (BCrypt)
    Note over Svc: Instantiate User entity<br/>Instantiate UserStats(level=1, points=0)
    Svc->>UserRepo: save(user)
    UserRepo->>DB: INSERT INTO users ...<br/>INSERT INTO user_stats ...
    DB-->>UserRepo: Saved rows
    Svc-->>Ctrl: UserSummaryDto
    deactivate Svc
    Ctrl-->>GW: HTTP 201 Created<br/>Body: UserSummaryDto
    GW-->>Client: HTTP 201 Created<br/>Body: UserSummaryDto
```

---

### Flow 2: Client Login & Session Issuance (`POST /api/v1/auth/login`)

```mermaid
sequenceDiagram
    autonumber
    actor Client as Browser / Client
    participant GW as API Gateway
    participant Ctrl as AuthController
    participant Svc as AuthService
    participant JwtSvc as JwtService
    participant DB as PostgreSQL

    Client->>GW: POST /api/v1/auth/login<br/>Header: User-Agent: Mozilla/5.0...<br/>Body: LoginRequest { loginIdentifier, password }
    GW->>Ctrl: Forward request
    Ctrl->>Svc: login(LoginRequest, userAgent)
    activate Svc
    Svc->>DB: findByEmailOrUsername(identifier)
    DB-->>Svc: User entity
    Svc->>Svc: BCrypt.matches(password, user.passwordHash)
    Note over Svc: Check user.status == ACTIVE
    Svc->>JwtSvc: generateAccessToken(user)
    Note over JwtSvc: Signs RS256 JWT with private key<br/>Claims: sub=userId, username, roles, exp=now+300s
    JwtSvc-->>Svc: accessToken string
    Svc->>JwtSvc: generateRefreshToken() & sha256(...)
    JwtSvc-->>Svc: rawRefreshToken, tokenHash, uaHash
    Note over Svc: Create RefreshToken entity (expiresAt = now + 7 days)
    Svc->>DB: INSERT INTO refresh_tokens (token_hash, user_agent_hash, ...)
    DB-->>Svc: Persisted
    Svc-->>Ctrl: AuthSession(AuthResponse, ResponseCookie)
    deactivate Svc
    Note over Ctrl: Sets Set-Cookie: refreshToken=...; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth
    Ctrl-->>GW: HTTP 200 OK<br/>Body: AuthResponse<br/>Set-Cookie: refreshToken=...
    GW-->>Client: HTTP 200 OK<br/>Body: AuthResponse (stored in JS memory)<br/>Set-Cookie (stored in browser cookie jar)
```

---

### Flow 3: Silent Refresh with Rotation (RTR) & Replay Defense (`POST /api/v1/auth/refresh`)

```mermaid
sequenceDiagram
    autonumber
    actor Client as Browser / Client
    participant GW as API Gateway
    participant Ctrl as AuthController
    participant Svc as AuthService
    participant JwtSvc as JwtService
    participant TokenRepo as RefreshTokenRepository
    participant DB as PostgreSQL

    Note over Client: In-memory Access Token expired (300s TTL)<br/>Silent Refresh Mutex deduplicates concurrent calls
    Client->>GW: POST /api/v1/auth/refresh<br/>Cookie: refreshToken=...<br/>Header: User-Agent: Mozilla/5.0...
    GW->>Ctrl: Forward request
    Ctrl->>Svc: refresh(rawRefreshToken, userAgent)
    activate Svc
    Svc->>JwtSvc: sha256(rawRefreshToken)
    JwtSvc-->>Svc: tokenHash
    Svc->>TokenRepo: findByTokenHash(tokenHash)
    TokenRepo->>DB: SELECT * FROM refresh_tokens WHERE token_hash = ?
    DB-->>TokenRepo: RefreshToken entity

    alt Replay Attack Detected (token.isRevoked() || token.isReplaced())
        Note over Svc: [SECURITY_ALERT: REFRESH_TOKEN_REPLAY]<br/>Revoke entire token family downstream (replacedById chain)
        Svc->>DB: UPDATE refresh_tokens SET revoked_at = now WHERE id IN (descendants)
        Svc-->>Ctrl: throw UnauthorizedException("Invalid or revoked refresh token")
        Ctrl-->>Client: HTTP 401 Unauthorized (ProblemDetails)
    else User-Agent Mismatch
        Note over Svc: [SECURITY_ALERT: USER_AGENT_MISMATCH]<br/>Revoke this token immediately
        Svc->>DB: UPDATE refresh_tokens SET revoked_at = now WHERE id = token.id
        Svc-->>Ctrl: throw UnauthorizedException("User-Agent mismatch")
        Ctrl-->>Client: HTTP 401 Unauthorized (ProblemDetails)
    else Token Expired (token.isExpired())
        Svc-->>Ctrl: throw UnauthorizedException("Refresh token has expired")
        Ctrl-->>Client: HTTP 401 Unauthorized (ProblemDetails)
    else Valid Token (Happy Path)
        Note over Svc: 1. Generate new refresh token & hash<br/>2. Persist new RefreshToken row<br/>3. Mark old token: revokedAt = now, replacedById = newToken.id<br/>4. Generate new RS256 Access Token
        Svc->>DB: INSERT INTO refresh_tokens (new token)<br/>UPDATE refresh_tokens SET revoked_at = now, replaced_by_id = new_id WHERE id = old_id
        DB-->>Svc: OK
        Svc-->>Ctrl: AuthSession(new AuthResponse, new RefreshCookie)
        deactivate Svc
        Ctrl-->>GW: HTTP 200 OK<br/>Body: AuthResponse<br/>Set-Cookie: rotated refreshToken
        GW-->>Client: HTTP 200 OK (New Access Token & Rotated Cookie)
    end
```

---

### Flow 4: User Profile & Gamification Stats (`GET /api/v1/users/me`)

```mermaid
sequenceDiagram
    autonumber
    actor Client as Browser / Client
    participant GW as API Gateway
    participant SecFilter as Spring Security (Resource Server)
    participant Ctrl as UserController
    participant Svc as UserService
    participant Calc as LevelCalculator
    participant DB as PostgreSQL

    Client->>GW: GET /api/v1/users/me<br/>Header: Authorization: Bearer <JWT>
    GW->>SecFilter: Forward request
    Note over SecFilter: NimbusJwtDecoder validates RS256 signature with jwt-public.pem<br/>Checks exp, iss. Extracts sub (userId)
    SecFilter->>Ctrl: getMyProfile(@AuthenticationPrincipal Jwt jwt)
    Ctrl->>Svc: getProfile(currentUserId)
    activate Svc
    Svc->>DB: findById(userId) [User with UserStats]
    DB-->>Svc: User & UserStats
    Svc->>Calc: calculateNextLevelPoints(stats.getLevel())
    Note over Calc: nextLevelPoints = currentLevel^2 * 50
    Calc-->>Svc: nextLevelPoints (long)
    Note over Svc: Build UserProfileResponse
    Svc-->>Ctrl: UserProfileResponse
    deactivate Svc
    Ctrl-->>GW: HTTP 200 OK<br/>Body: UserProfileResponse
    GW-->>Client: HTTP 200 OK<br/>Body: UserProfileResponse
```

---

### Flow 5: Global Leaderboard & Individual Rank (`GET /api/v1/leaderboard`)

```mermaid
sequenceDiagram
    autonumber
    actor Client as Browser / Client
    participant GW as API Gateway
    participant Ctrl as LeaderboardController
    participant Svc as LeaderboardService
    participant StatsRepo as UserStatsRepository
    participant DB as PostgreSQL

    Client->>GW: GET /api/v1/leaderboard?page=0&size=20<br/>Header: Authorization: Bearer <JWT>
    GW->>Ctrl: getGlobalLeaderboard(jwt, page=0, size=20)
    Ctrl->>Svc: getGlobalLeaderboard(currentUserId, page, size)
    activate Svc
    Svc->>StatsRepo: findLeaderboard(PageRequest.of(0, 20))
    StatsRepo->>DB: SELECT s FROM UserStats s JOIN FETCH s.user<br/>ORDER BY s.totalPoints DESC, s.updatedAt ASC
    DB-->>StatsRepo: Page<UserStats> (20 items)
    Note over Svc: Maps each item to LeaderboardEntryDto(rank = startRank + i)

    alt Current user is inside current page (In-Page)
        Note over Svc: myRank assigned directly from page content (0 extra DB queries)
    else Current user is outside current page (Out-of-Page)
        Svc->>StatsRepo: calculateRank(myPoints)
        StatsRepo->>DB: SELECT COUNT(s) + 1 FROM UserStats s WHERE s.totalPoints > :myPoints
        DB-->>StatsRepo: rank number (e.g. 142)
        Note over Svc: Assembles myRank LeaderboardEntryDto
    end

    Svc-->>Ctrl: LeaderboardResponse(entries, myRank, page, size, totalElements, totalPages)
    deactivate Svc
    Ctrl-->>GW: HTTP 200 OK<br/>Body: LeaderboardResponse
    GW-->>Client: HTTP 200 OK<br/>Body: LeaderboardResponse
```

---

### Flow 6: Internal Learning Service Points Awarding (`POST /internal/v1/users/{userId}/points`)

```mermaid
sequenceDiagram
    autonumber
    participant Learning as Learning Service
    participant ApiKeyFilter as InternalApiKeyFilter
    participant Ctrl as InternalUserController
    participant Svc as PointService
    participant TxRepo as PointTransactionRepository
    participant StatsRepo as UserStatsRepository
    participant DB as PostgreSQL

    Learning->>ApiKeyFilter: POST /internal/v1/users/{userId}/points<br/>Header: X-Internal-Api-Key: <key><br/>Body: AddPointsRequest { points, sourceType, sourceId, idempotencyKey }
    
    Note over ApiKeyFilter: Constant-time comparison:<br/>MessageDigest.isEqual(configuredKey, headerKey)
    alt Invalid or Missing API Key
        ApiKeyFilter-->>Learning: HTTP 401 Unauthorized (ProblemDetails: INTERNAL_API_KEY_INVALID)
    else Valid Internal Key
        ApiKeyFilter->>Ctrl: addPoints(userId, AddPointsRequest)
        Ctrl->>Svc: addPoints(userId, request)
        activate Svc

        %% Step 1: Idempotency Check
        Svc->>TxRepo: findByIdempotencyKey(idempotencyKey)
        TxRepo->>DB: SELECT * FROM point_transactions WHERE idempotency_key = ?
        DB-->>TxRepo: Result

        alt Transaction Already Exists (Duplicate / Retry)
            Note over Svc: Verify matching userId (or throw 409 Conflict if hijacked)
            Svc->>StatsRepo: findById(userId)
            StatsRepo->>DB: SELECT * FROM user_stats WHERE user_id = ?
            DB-->>StatsRepo: Current UserStats
            Note over Svc: Return existing transaction result with duplicate: true
            Svc-->>Ctrl: PointTransactionResponse (duplicate: true)
        else First-Time Execution (New Transaction)
            Svc->>DB: SELECT * FROM users WHERE id = ?
            DB-->>Svc: User entity
            Svc->>StatsRepo: findById(userId)
            DB-->>Svc: UserStats (version=V)
            Note over Svc: newTotal = totalPoints + points<br/>newLevel = floor(sqrt(newTotal / 50)) + 1
            Note over Svc: Optimistic lock (@Version) protects user_stats
            Svc->>StatsRepo: save(updatedStats)
            StatsRepo->>DB: UPDATE user_stats SET total_points = ?, level = ?, version = V+1 WHERE user_id = ? AND version = V
            Svc->>TxRepo: save(PointTransaction)
            TxRepo->>DB: INSERT INTO point_transactions (id, user_id, points, idempotency_key, ...)
            DB-->>Svc: Persisted
            Svc-->>Ctrl: PointTransactionResponse (duplicate: false)
        end
        deactivate Svc
        Ctrl-->>Learning: HTTP 200 OK<br/>Body: PointTransactionResponse
    end
```

---

### Flow 7: Internal Social Service Batch Stats Retrieval (`POST /internal/v1/users/public-stats:batch`)

```mermaid
sequenceDiagram
    autonumber
    participant Social as Social Service
    participant ApiKeyFilter as InternalApiKeyFilter
    participant Ctrl as InternalUserController
    participant Svc as PointService
    participant StatsRepo as UserStatsRepository
    participant DB as PostgreSQL

    Social->>ApiKeyFilter: POST /internal/v1/users/public-stats:batch<br/>Header: X-Internal-Api-Key: <key><br/>Body: BatchStatsRequest { userIds: [uuid1, uuid2, ...] }
    Note over ApiKeyFilter: Validates X-Internal-Api-Key via constant-time compare
    ApiKeyFilter->>Ctrl: getBatchStats(BatchStatsRequest)
    Ctrl->>Svc: getBatchStats(request.userIds())
    activate Svc
    Note over Svc: Deduplicate input UUIDs (up to 100)
    Svc->>StatsRepo: findAllWithUserByUserIds(distinctIds)
    StatsRepo->>DB: SELECT s FROM UserStats s JOIN FETCH s.user<br/>WHERE s.userId IN (:distinctIds)
    Note over DB: Single query prevents N+1 query problem!
    DB-->>StatsRepo: List<UserStats> with eagerly loaded User
    Note over Svc: Map each UserStats to PublicProfileDto(id, username, displayName, level, totalPoints)
    Svc-->>Ctrl: List<PublicProfileDto>
    deactivate Svc
    Ctrl-->>Social: HTTP 200 OK<br/>Body: List<PublicProfileDto>
```

---

### Flow 8: Zero-Trust Local JWT Validation in Internal Microservices

```mermaid
sequenceDiagram
    autonumber
    actor Client as Browser / Client
    participant GW as API Gateway
    participant InternalSvc as Learning / Social Service
    participant LocalSec as NimbusJwtDecoder (Local Resource Server)
    participant Ctrl as Service Controller
    participant KeyFile as Shared File: jwt-public.pem

    Client->>GW: GET /api/v1/lessons/active<br/>Header: Authorization: Bearer <JWT>
    Note over GW: Edge Gateway forwards Bearer Token intact.<br/>Does NOT strip or terminate authorization.
    GW->>InternalSvc: GET /api/v1/lessons/active<br/>Header: Authorization: Bearer <JWT>
    
    InternalSvc->>LocalSec: Filter incoming Bearer token
    Note over LocalSec: 1. Reads local jwt-public.pem<br/>2. Verifies RS256 signature mathematically (NO HTTP call to Identity!)<br/>3. Verifies issuer == 'szoftarch-nyelvtanulo'<br/>4. Verifies exp > now (300s TTL)<br/>5. Extracts sub (userId), username, roles
    
    alt Signature Invalid or Token Expired
        LocalSec-->>InternalSvc: Rejection
        InternalSvc-->>Client: HTTP 401 Unauthorized (RFC 9457 ProblemDetails)
    else Token Authenticated
        LocalSec->>Ctrl: Inject @AuthenticationPrincipal Jwt jwt into SecurityContext
        Note over Ctrl: UUID currentUserId = UUID.fromString(jwt.getSubject());<br/>Enforce IDOR check: currentUserId == lesson.ownerId
        Ctrl-->>Client: HTTP 200 OK (Domain Response)
    end
```

---

## 5. Comprehensive Request-Response & Data Mapping Matrix

| Category | HTTP Endpoint | Security & Headers / Cookies | Request Body DTO | Response Body DTO / Headers / Cookies | Involved JPA Entities | Key Invariants & Business Logic |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Auth** | `POST /api/v1/auth/register` | Public | [RegisterRequest](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/api/dto/RegisterRequest.java)<br/>`email`, `username`, `displayName`, `password` | **201 Created**<br/>[UserSummaryDto](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/api/dto/UserSummaryDto.java) | [User](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/domain/User.java),<br/>[UserStats](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/domain/UserStats.java) | BCrypt password hashing; normalized email/username uniqueness check; initializes stats (level=1, points=0). |
| **Auth** | `POST /api/v1/auth/login` | Public<br/>Header: `User-Agent` | [LoginRequest](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/api/dto/LoginRequest.java)<br/>`loginIdentifier`, `password` | **200 OK**<br/>[AuthResponse](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/api/dto/AuthResponse.java)<br/>`Set-Cookie: refreshToken=...; HttpOnly; SameSite=Strict; Path=/api/v1/auth; Max-Age=604800` | [User](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/domain/User.java),<br/>[RefreshToken](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/domain/RefreshToken.java) | Authenticates email/username; checks active status; issues 5-min RS256 access token; persists SHA-256 hashed refresh token with User-Agent hash. |
| **Auth** | `POST /api/v1/auth/refresh` | Cookie: `refreshToken`<br/>Header: `User-Agent` | *None* | **200 OK**<br/>[AuthResponse](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/api/dto/AuthResponse.java)<br/>`Set-Cookie: refreshToken=...; HttpOnly; SameSite=Strict; ...` | [RefreshToken](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/domain/RefreshToken.java),<br/>[User](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/domain/User.java) | Refresh Token Rotation (RTR); Replay detection with downstream token family revocation; strict User-Agent hash verification. |
| **Auth** | `POST /api/v1/auth/logout` | Cookie: `refreshToken` | *None* | **204 No Content**<br/>`Set-Cookie: refreshToken=; Max-Age=0; Path=/api/v1/auth` | [RefreshToken](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/domain/RefreshToken.java) | Marks DB refresh token `revokedAt = now`; clears client cookie immediately. |
| **Auth** | `POST /api/v1/auth/change-password` | Bearer JWT | [ChangePasswordRequest](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/api/dto/ChangePasswordRequest.java)<br/>`currentPassword`, `newPassword` | **200 OK**<br/>[AuthResponse](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/api/dto/AuthResponse.java)<br/>`Set-Cookie: refreshToken=...` | [User](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/domain/User.java),<br/>[RefreshToken](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/domain/RefreshToken.java) | Verifies current password; updates BCrypt hash; invokes `revokeAllForUser` to invalidate all active refresh tokens globally; issues fresh session for caller. |
| **Profile** | `GET /api/v1/users/me` | Bearer JWT | *None* | **200 OK**<br/>[UserProfileResponse](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/api/dto/UserProfileResponse.java) | [User](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/domain/User.java),<br/>[UserStats](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/domain/UserStats.java) | Resolves user by JWT `sub`; computes `nextLevelPoints = level^2 * 50` in-memory. |
| **Profile** | `PATCH /api/v1/users/me` | Bearer JWT | [UpdateProfileRequest](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/api/dto/UpdateProfileRequest.java)<br/>`username?`, `displayName?` | **200 OK**<br/>[UserProfileResponse](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/api/dto/UserProfileResponse.java) | [User](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/domain/User.java),<br/>[UserStats](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/domain/UserStats.java) | Validates username uniqueness and format; updates display name; recalculates stats projection. |
| **Profile** | `GET /api/v1/users/search?username={q}` | Bearer JWT | Query param: `username` | **200 OK**<br/>`List<PublicProfileDto>` | [User](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/domain/User.java),<br/>[UserStats](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/domain/UserStats.java) | Prefix search on lower(username) using B-tree index; limits results to max 20 users. |
| **Profile** | `GET /api/v1/users/{id}/public` | Bearer JWT | Path param: `id` (UUID) | **200 OK**<br/>[PublicProfileDto](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/api/dto/PublicProfileDto.java) | [User](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/domain/User.java),<br/>[UserStats](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/domain/UserStats.java) | Returns public gamified profile without sensitive fields (no email, no password hash). |
| **Leaderboard** | `GET /api/v1/leaderboard`<br/>`GET /api/v1/leaderboards/global` | Bearer JWT (optional/auth)<br/>Query: `page`, `size` | *None* | **200 OK**<br/>[LeaderboardResponse](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/api/dto/LeaderboardResponse.java) | [UserStats](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/domain/UserStats.java),<br/>[User](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/domain/User.java) | Paginated query with `JOIN FETCH`; calculates `myRank` in-page (0 extra DB queries) or out-of-page via `COUNT(points > myPoints) + 1`. |
| **Leaderboard** | `GET /api/v1/leaderboard/me` | Bearer JWT | *None* | **200 OK**<br/>[LeaderboardEntryDto](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/api/dto/LeaderboardEntryDto.java) | [UserStats](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/domain/UserStats.java),<br/>[User](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/domain/User.java) | Calculates individual rank and standing of the current user. |
| **Internal (Learning)** | `POST /internal/v1/users/{userId}/points` | Header: `X-Internal-Api-Key`<br/>Path: `userId` (UUID) | [AddPointsRequest](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/api/dto/AddPointsRequest.java)<br/>`points`, `sourceType`, `sourceId`, `idempotencyKey` | **200 OK**<br/>[PointTransactionResponse](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/api/dto/PointTransactionResponse.java) | [PointTransaction](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/domain/PointTransaction.java),<br/>[UserStats](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/domain/UserStats.java) (with `@Version`),<br/>[User](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/domain/User.java) | Guaranteed idempotency via unique `idempotency_key` constraint; duplicate calls return original result with `duplicate: true`; optimistic lock protects `UserStats`. |
| **Internal (Social)** | `POST /internal/v1/users/public-stats:batch` | Header: `X-Internal-Api-Key` | [BatchStatsRequest](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/api/dto/BatchStatsRequest.java)<br/>`userIds: List<UUID>` (max 100) | **200 OK**<br/>`List<PublicProfileDto>` | [UserStats](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/domain/UserStats.java),<br/>[User](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/domain/User.java) | Batch fetch of user stats using single `JOIN FETCH` query; eliminates $N+1$ performance issue. |
| **Error (All)** | *Any failing endpoint* | N/A | N/A | **400 / 401 / 404 / 409 / 500**<br/>`Content-Type: application/problem+json`<br/>[ProblemDetails](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/api/dto/ProblemDetails.java) | N/A | RFC 9457 compliant error structure with error `code`, HTTP status, trace/correlation ID, and detailed validation `fieldErrors`. |

---

## 6. Detailed DTO vs JPA Data Mapping Specification

### 6.1. Why Complete Decoupling is Crucial
1. **Mass-Assignment (Over-Posting) Prevention:**
   If the controllers accepted `User` directly, a malicious client could craft a JSON payload containing `"status": "BLOCKED"` or tamper with `"version"` or `"id"`. By accepting only `RegisterRequest` or `UpdateProfileRequest`, the API boundary enforces that only permitted fields can enter the system.
2. **Data Leakage Prevention:**
   `User` contains sensitive fields (`passwordHash`). Returning the entity directly would risk serialization bugs exposing hashes. Using `UserSummaryDto`, `UserProfileResponse`, and `PublicProfileDto` guarantees that credentials never reach the network.
3. **Database Schema Evolution:**
   Tables can be partitioned, columns renamed, or indexes restructured without breaking the contract specified in [identity-api.yaml](file:///home/istvan/szoftarch_projekt/contracts/openapi/identity-api.yaml).

### 6.2. Field-by-Field Mapping Reference

#### `User` Entity $\longleftrightarrow$ DTOs
```
User Entity                          DTO Field                              Purpose
====================================================================================================
id (UUID)                        --> UserSummaryDto.id                      Identity anchor
                                 --> UserProfileResponse.id                 Profile identifier
                                 --> PublicProfileDto.id                    Public ID for Social
email (String)                   <-- RegisterRequest.email                  Registration input
                                 --> UserSummaryDto.email                   Summary output
                                 --> UserProfileResponse.email              Private profile output
username (String)                <-- RegisterRequest.username               Registration input
                                 <-- UpdateProfileRequest.username          Profile update input
                                 --> UserSummaryDto.username                Summary output
                                 --> UserProfileResponse.username           Private profile output
                                 --> PublicProfileDto.username              Public search/social
                                 --> LeaderboardEntryDto.username           Leaderboard row
displayName (String)             <-- RegisterRequest.displayName            Registration input
                                 <-- UpdateProfileRequest.displayName       Profile update input
                                 --> UserSummaryDto.displayName             Summary output
                                 --> UserProfileResponse.displayName        Private profile output
                                 --> PublicProfileDto.displayName           Public profile
                                 --> LeaderboardEntryDto.displayName        Leaderboard row
passwordHash (String)            <-- BCrypt(RegisterRequest.password)       Hashed before save; NEVER exposed in DTOs
status (UserStatus)              (internal only)                            Active check; suspended users blocked
createdAt (Instant)              --> UserSummaryDto.createdAt               Registration timestamp
                                 --> UserProfileResponse.createdAt          Profile creation timestamp
```

#### `UserStats` Entity $\longleftrightarrow$ DTOs & Engine
```
UserStats Entity                     DTO / Calculated Field                 Purpose
====================================================================================================
totalPoints (long)               --> UserProfileResponse.totalPoints        Total XP for user profile
                                 --> PublicProfileDto.totalPoints           Total XP for public/social
                                 --> LeaderboardEntryDto.totalPoints        XP displayed in leaderboard
                                 --> PointTransactionResponse.totalPoints   Post-transaction XP
level (int)                      --> UserProfileResponse.level              Current level
                                 --> PublicProfileDto.level                 Public level
                                 --> LeaderboardEntryDto.level              Level in leaderboard
                                 --> PointTransactionResponse.level         Post-transaction level
[LevelCalculator]                --> UserProfileResponse.nextLevelPoints    level^2 * 50
                                 --> PointTransactionResponse.nextLevelPoints
version (Long)                   (internal @Version)                        Optimistic locking (Lost Update protection)
```

#### `PointTransaction` Entity $\longleftrightarrow$ DTOs
```
PointTransaction Entity              DTO Field                              Purpose
====================================================================================================
id (UUID)                        --> PointTransactionResponse.transactionId Identifier of transaction
points (int)                     <-- AddPointsRequest.points                Points awarded from lesson
                                 --> PointTransactionResponse.pointsAdded   Echoed in response
sourceType (String)              <-- AddPointsRequest.sourceType            Domain event source (e.g. LESSON)
sourceId (String)                <-- AddPointsRequest.sourceId              Lesson or quiz ID
idempotencyKey (String)          <-- AddPointsRequest.idempotencyKey        Unique constraint; retry guard
occurredAt (Instant)             --> PointTransactionResponse.occurredAt    Transaction timestamp
```

#### `RefreshToken` Entity $\longleftrightarrow$ HTTP Cookies
```
RefreshToken Entity                  Transport Mechanism                    Security Details
====================================================================================================
tokenHash (String)               <-- SHA256(rawRefreshToken)                Raw token sent in HttpOnly cookie
expiresAt (Instant)              <-- now + 604800s                          Enforced on refresh
revokedAt (Instant)              (internal DB state)                        Populated on logout or rotation
replacedById (UUID)              (internal DB state)                        Points to next token in RTR chain
userAgentHash (String)           <-- SHA256(HttpServletRequest.User-Agent)  Strict rejection on platform change
```

---

## 7. Error Handling & RFC 9457 Problem Details Mapping

All controller exceptions are mapped through [GlobalExceptionHandler](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/api/GlobalExceptionHandler.java) and [SecurityConfig](file:///home/istvan/szoftarch_projekt/services/identity-service/src/main/java/hu/bme/szoftarch/identity/infrastructure/SecurityConfig.java) into standard RFC 9457 `application/problem+json` responses:

```mermaid
flowchart TD
    Ex[Exception Thrown in Business / Security Layer] --> TypeCheck{Exception Class}

    TypeCheck -->|MethodArgumentNotValidException| V[400 Bad Request<br/>code: VALIDATION_ERROR<br/>populates fieldErrors List]
    TypeCheck -->|UnauthorizedException / BadCredentials| U[401 Unauthorized<br/>code: UNAUTHORIZED / BAD_CREDENTIALS]
    TypeCheck -->|InternalApiKeyFilter Rejection| K[401 Unauthorized<br/>code: INTERNAL_API_KEY_INVALID]
    TypeCheck -->|NotFoundException| N[404 Not Found<br/>code: NOT_FOUND]
    TypeCheck -->|ConflictException / Duplicate Email| C[409 Conflict<br/>code: CONFLICT / EMAIL_TAKEN / USERNAME_TAKEN]
    TypeCheck -->|OptimisticLockingFailureException| O[409 Conflict<br/>Concurrent modification detected]
    TypeCheck -->|Unhandled RuntimeException| E[500 Internal Server Error<br/>code: INTERNAL_SERVER_ERROR]

    V --> Problem[ProblemDetails Record]
    U --> Problem
    K --> Problem
    N --> Problem
    C --> Problem
    O --> Problem
    E --> Problem

    Problem --> JSON["application/problem+json<br/>@JsonInclude(NON_NULL)"]
```
