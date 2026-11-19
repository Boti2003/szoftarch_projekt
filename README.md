# Közösségi nyelvtanuló alkalmazás

Játékosított, közösségi nyelvtanuló webalkalmazás mikroszolgáltatásos
architektúrával. Ez a repository a **0. fázis skeletonja**: minden komponens
elindul és health-et ad, az üzleti logikát a service-gazdák fejlesztik a saját
feature branchükön.

A részletes fejlesztési terv: [`../TERV.md`](../TERV.md).

## Architektúra

```
Browser → Nginx (TLS) → Gateway → { Identity, Learning, Social } → PostgreSQL
                                     Learning → AI (belső)
```

- **Backend:** Java 21, Spring Boot 4.1.2, Spring Cloud Gateway (Maven reactor).
- **Frontend:** React 19 + TypeScript + Vite 8 + MUI.
- **Adatbázis:** PostgreSQL, database-per-service, Flyway migrációk.
- **Hitelesítés:** RS256 JWT + rotált refresh token.

| Komponens | Modul | DB | Port (belső) |
|---|---|---|---|
| Gateway | `gateway/` | – | 8080 |
| Identity | `services/identity-service/` | `identity_db` | 8080 |
| Learning | `services/learning-service/` | `learning_db` | 8080 |
| Social | `services/social-service/` | `social_db` | 8080 |
| AI | `services/ai-service/` | `ai_db` | 8080 |
| Frontend + TLS | `frontend/`, `infra/nginx/` | – | 443 (publikus) |

## Indítás

Előfeltételek: Docker + Docker Compose.

```bash
cp .env.example .env
# Fejlesztői JWT kulcsok (RS256):
./infra/scripts/gen-jwt-keys.sh        # Windows: infra\scripts\gen-jwt-keys.ps1
docker compose up --build
```

Ezután HTTPS-en elérhető: <https://localhost> (a self-signed tanúsítványt
egyszer el kell fogadni a böngészőben). A nyitóoldal egy health dashboard, amely
a Gateway-en keresztül mutatja a service-ek állapotát.

## Fejlesztés lokálisan (Docker nélkül)

Backend (egy service):

```bash
./mvnw -pl :identity-service spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173, /api proxy → localhost:8080
```

## Tesztek

```bash
./mvnw test          # backend reactor
cd frontend && npm test
```

## Repository-struktúra

```
pom.xml                 Maven reactor (gateway + 4 service)
mvnw / mvnw.cmd         Maven Wrapper
compose.yaml            Egyparancsos indítás
.env.example            Környezeti változók sablonja
contracts/openapi/      API szerződések (OpenAPI 3.1 vázak)
gateway/                API Gateway
services/               Identity / Learning / Social / AI service
frontend/               React + Vite + MUI
infra/                  Nginx (TLS), PostgreSQL init, scriptek
.github/workflows/      CI
```

Minden service a `api → application → domain → infrastructure` réteges
szerkezetet követi.

## Munkamódszer (4 fő)

1. A skeleton a `main`-en van; erre épül minden.
2. Mindenki a saját service-ét külön feature branchen fejleszti
   (`feature/identity`, `feature/learning`, `feature/social`, `feature/ai`).
3. A közös fájlokat (`pom.xml` reactor, `compose.yaml`, `contracts/`,
   `.env.example`) csak egyeztetett PR-ben módosítjuk.
4. Minden PR-t másik vertikum gazdája review-z.
