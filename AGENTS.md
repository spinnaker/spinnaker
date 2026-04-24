# AI Coding Agent Instructions

## Purpose
- Spinnaker is an open-source continuous delivery platform for releasing software with high velocity and confidence.
- Provides multi-cloud deployment support (AWS, GCP, Kubernetes, Azure, CloudFoundry, etc.) with automated canary analysis.
- This monorepo consolidates all Spinnaker microservices and UI components for unified development.

## Rules
- Groovy is deprecated.  Write all backend in java or kotlin.  If modifying a groovy class, rewrite it to java first.
- always run ./gradlew from the root of the repository


## Architecture
- **Type:** Monorepo with Gradle composite builds
- **Backend:** Java/Kotlin (Spring Boot microservices)
- **Frontend:** TypeScript/React (deck, deck-kayenta)
- **Build System:** Gradle (backend), Yarn/Webpack (frontend)
- **Storage:** Redis (queues/caching), MySQL/SQL (persistence)

### Microservices

| Service       | Purpose                       | Debug Port |
|---------------|-------------------------------|------------|
| `clouddriver` | Cloud provider integrations   | 7002       |
| `orca`        | Orchestration engine          | 8083       |
| `gate`        | REST API gateway              | 8084       |
| `front50`     | Metadata persistence          | 8080       |
| `echo`        | Event routing/CRON scheduling | 8089       |
| `igor`        | CI/SCM integrations           | 8088       |
| `fiat`        | Authorization service         | 7003       |
| `rosco`       | Image bakery (Packer/Helm)    | 8087       |
| `kayenta`     | Automated canary analysis     | 8090       |
| `kork`        | Shared service libraries      | -          |
| `deck`        | Spinnaker UI                  | 9000       |

## Development Environment

### Setup (Backend)
```bash
# Build all backend services
./gradlew build
```

### Setup (Frontend - deck)
```bash
cd deck
yarn
yarn modules
yarn build
```

### Navigation
- `/clouddriver/` - Cloud provider service
- `/orca/` - Orchestration engine
- `/gate/` - API gateway
- `/front50/` - Metadata store
- `/echo/` - Events/notifications
- `/igor/` - CI integrations
- `/fiat/` - Authorization
- `/rosco/` - Image bakery
- `/kayenta/` - Canary analysis
- `/kork/` - Shared libraries
- `/deck/` - UI (React/TypeScript)
- `/deck-kayenta/` - Canary UI plugin

## Build & Test

### Backend (Gradle)
```bash
./gradlew build          # Build all services
./gradlew test           # Run all tests
./gradlew :orca:test     # Test single service
./gradlew spotlessApply  # Apply formatting
```

### Frontend (deck)
```bash
cd deck
yarn build               # Production build
yarn test                # Run unit tests
yarn lint                # ESLint check
yarn prettier:check      # Check formatting
yarn prettier            # Apply formatting
```

### Frontend (deck-kayenta)
```bash
cd deck-kayenta
npm run build
npm run test
npm run lint
```

## Testing Strategy
- Prefer running single service tests: `./gradlew :servicename:test`
- Backend uses JUnit 5 (via `useJUnitPlatform()`)
- Frontend uses Karma (deck) and Jest (deck-kayenta)
- Fix all test/type errors before committing
- Run `./gradlew spotlessApply` / `yarn lint` before commits

## Repository Map
```
/                       # Root Gradle composite build
/clouddriver/           # Cloud provider integrations
/orca/                  # Pipeline orchestration
/gate/                  # API gateway
/front50/               # Metadata persistence
/echo/                  # Event routing
/igor/                  # CI/SCM integration
/fiat/                  # Authorization
/rosco/                 # Image bakery
/kayenta/               # Canary analysis
/kork/                  # Shared libraries
/deck/                  # Main UI (React)
/deck/packages/         # UI workspace packages
/deck-kayenta/          # Canary UI plugin
/spinnaker-gradle-project/ # Gradle plugins
```

## Code Style
- Backend: Use Spotless for Java/Kotlin formatting (`./gradlew spotlessApply`)
- Frontend: Prettier + ESLint for TypeScript/JavaScript
- Enable Lombok annotation processing in IDE for backend development
- New UI changes should use React (not Angular)

## Git & PR Policy
- **Commits:** Ask permission before pushing
- **PRs:** Create as drafts (`gh pr create --draft`)
- Run `./gradlew spotlessApply` and `yarn lint` before committing
- Ensure tests pass locally before pushing

## Security Considerations
- Never commit secrets, API keys, or credentials
- Be cautious with cloud provider configurations
- Review authorization changes in Fiat carefully
- Validate input in Gate API endpoints
- Follow OWASP guidelines for web security in deck
