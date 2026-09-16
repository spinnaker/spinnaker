# AI Coding Agent Instructions

## Purpose
- Spinnaker is an open-source continuous delivery platform for releasing software with high velocity and confidence.
- Provides multi-cloud deployment support (AWS, GCP, Kubernetes, Azure, CloudFoundry, etc.) with automated canary analysis.
- This monorepo consolidates all Spinnaker microservices and UI components for unified development.

## Architecture
- **Type:** Monorepo with Gradle composite builds
- **Backend:** Java/Kotlin (Spring Boot microservices)
- **Frontend:** TypeScript/React (Deck, including `deck/packages/kayenta`)
- **Build System:** Gradle (backend), pnpm/Webpack (frontend)
- **Storage:** Redis (queues/caching), MySQL/SQL (persistence)

### Microservices

| Service       | Purpose                       | Default Port |
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
| `keel`        | Declarative delivery          | 7010       |
| `kork`        | Shared service libraries      | -          |
| `deck`        | Spinnaker UI                  | 9000       |

## Development Environment

### Backend
Use the repository's Gradle wrapper. No separate backend dependency-install step is required;
the wrapper resolves Gradle and project dependencies when running the tasks below.

### Frontend (Deck)
Use the Node and pnpm versions declared in `deck/package.json` (`engines.node` and
`packageManager`). Deck uses a pnpm workspace and lockfile; don't substitute npm or Yarn.

Run this command from the `deck/` directory to install workspace dependencies:

```bash
pnpm install
pnpm modules
pnpm build
```

## Build & Test

### Backend (Gradle)
```bash
./gradlew build          # Build all main backend composite builds
./gradlew test           # Test all main backend composite builds
./gradlew :orca:test     # Test single service
./gradlew spotlessCheck  # Check code formatting
./gradlew spotlessApply  # Apply formatting
```

### Running services
Service-name Gradle tasks such as `./gradlew orca` and the aggregate `./gradlew run` start
long-running processes. They are not build or test checks; use them only when an intentional
runtime smoke test requires a running service.

### Frontend (deck)
Run these commands from the `deck/` directory.

```bash
pnpm modules             # Build Deck workspace modules
pnpm build               # Production build
pnpm test                # Run workspace-wide Deck unit tests
pnpm lint                # Lint all Deck packages
pnpm prettier:check      # Check formatting
pnpm prettier            # Apply formatting
```

### Frontend (Deck Kayenta package)
Run these commands from the `deck/` directory.

```bash
pnpm --filter @spinnaker/kayenta build  # Build only the Kayenta package
# Kayenta has no package-scoped test or lint scripts; use the root commands above.
```

## Testing Strategy
- Prefer running single service tests: `./gradlew :servicename:test`
- Backend tests run on the JUnit Platform; new Java tests use JUnit 5, while some existing suites use Groovy/Spock
- Frontend uses Karma for Deck packages, including Kayenta
- Kotlin modules can define Detekt checks in addition to Spotless; inspect the target module's
  Gradle configuration and run them when configured
- Fix all test/type errors introduced by the change before committing
- Run the applicable formatting and lint checks before committing: `./gradlew spotlessCheck`
  for backend changes and `pnpm lint` from `deck/` for frontend changes

### Choosing validation scope
- Start with the narrowest test or build task that exercises the changed behavior, then expand
  validation according to the affected dependency graph.
- Use [`.github/dependencies.yml`](.github/dependencies.yml) as the source of truth for CI
  service fan-out. Changes to shared builds such as `kork` or `fiat` can require validation
  of downstream services; a passing shared-module test alone may not be sufficient.
- Root `./gradlew test` validates the main backend composite builds. Deck, Spin, and
  `spinnaker-gradle-project` use separate lifecycle and validation commands.
- Clouddriver conditionally includes provider and artifact subprojects according to
  `clouddriver/settings.gradle`; inspect that configuration before assuming a subproject is
  part of the active build.
- If broader validation fails, determine whether the change introduced the failure. Do not
  modify unrelated code solely to make a pre-existing failure pass; report it separately.

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
/keel/                  # Declarative delivery
/kork/                  # Shared libraries
/deck/                  # Main UI (React)
/deck/packages/         # UI workspace packages
/deck/packages/kayenta/ # Canary UI package
/spin/                  # Spinnaker CLI; separate build lifecycle
/spinnaker-gradle-project/ # Gradle plugins; separate build lifecycle
```

## Code Style
- Backend: Use Spotless for Java/Kotlin formatting (`./gradlew spotlessApply`)
- Frontend: Prettier + ESLint for TypeScript/JavaScript
- Enable Lombok annotation processing in IDE for backend development
- New UI changes should use React (not Angular)
- **Before first writing or reviewing code in a session, read [CODE_STYLE.md](CODE_STYLE.md). Revisit relevant sections if the scope changes.** It captures the maintainers' conventions and roadmap constraints (what tooling doesn't enforce), for both writing conforming code and reviewing PRs.

## Git & PR Policy
- **Pushes:** Ask permission before pushing
- **PRs:** Create as drafts (`gh pr create --draft`)
- Ensure applicable tests pass locally before pushing

## Security Considerations
- Never commit secrets, API keys, or credentials
- Be cautious with cloud provider configurations
- Review authorization changes in Fiat carefully
- Validate input in Gate API endpoints
- Follow the [OWASP Top 10](https://owasp.org/www-project-top-ten/) when making security-relevant changes in Deck
