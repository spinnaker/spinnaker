# Jackson 3 Repository Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move all Spinnaker backend Jackson consumers from Jackson 2 APIs and artifacts to Jackson 3 while preserving serialization, Retrofit, Spring, Redis, AWS, and custom-mapper behavior.

**Architecture:** Spring Boot 4.1's `tools.jackson` mapper is the canonical mapper. Kork owns shared mapper customization and the Retrofit converter boundary; services consume injected mappers rather than constructing parallel mappers. Jackson 2 compatibility is retained only where existing serialized annotations require it, and the compatibility layer is removed after all runtime consumers use Jackson 3.

**Tech Stack:** Java 17, Kotlin, Groovy, Gradle composite builds, Spring Boot 4.1.1, Jackson 3.1.5, Retrofit 2.12.0, Spring Data Redis 4.1.1, JUnit 5, Spock, Spotless, Detekt.

**Spec:** User-approved repository-wide Jackson 2 to Jackson 3 migration, based on the green Spring Boot baseline in PR #7996 (`https://github.com/spinnaker/spinnaker/pull/7996`). No separate specification file exists.

## Global Constraints

- Base this branch on the green head of #7996 (`934d9b3de0f9eab4d2cab8f494a392767aa8e104`); do not modify the #7996 branch.
- Keep Java 17 as the toolchain; defer the Java 25 upgrade until this migration is complete.
- Use Jackson `3.1.5` and Spring Boot `4.1.1` versions already managed by the repository platform.
- Use `tools.jackson` for Jackson 3 core, databind, dataformat, and module APIs; retain `com.fasterxml.jackson.annotation` only where Jackson 3 requires that annotation package.
- Do not retain `com.squareup.retrofit2:converter-jackson`; the shared Kork converter must provide Retrofit request and response conversion with the injected Jackson 3 mapper.
- Write a failing behavioral test before each production behavior change; run the focused test, then the affected module compile/test, before moving to the next slice.
- Preserve public method behavior, response body closing, request media type, generic type handling, exception context, registered modules, and Spring bean extension points.
- Prefer Java/JUnit 5 for new tests; preserve existing Spock tests when modifying their surrounding behavior.
- Reuse Kork and Spring-managed infrastructure. Do not add a duplicate ObjectMapper, AWS module, serializer, or client converter.
- Run `./gradlew spotlessApply` only after a passing behavior slice and run `./gradlew spotlessCheck` before completion.
- Do not push, create a PR, or commit without explicit user permission.

---

### Task 1: Record Baseline And Census

**Files:**
- Create: `docs/superpowers/plans/2026-09-13-jackson-3-migration.md`
- Inspect: `settings.gradle`, `kork/settings.gradle`, `clouddriver/settings.gradle`, `kork/spinnaker-dependencies/spinnaker-dependencies.gradle`
- Inspect: `kork/kork-web/kork-web.gradle`, `kork/kork-retrofit/kork-retrofit.gradle`, `kork/kork-retrofit2/kork-retrofit2.gradle`, `keel/keel-retrofit/keel-retrofit.gradle`

**Interfaces:**
- Produces the verified baseline and the list of migration surfaces used by every later task.

- [x] **Step 1: Verify the branch and worktree**

Run:

```bash
git status --short --branch
```

Expected: branch `feat/jackson-3-migration` is based on the green #7996 head and no unrelated changes are present.

- [x] **Step 2: Verify the Kork baseline**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :kork:kork-web:compileJava :kork:kork-aws:compileJava :kork:kork-core:compileJava
```

Expected: `BUILD SUCCESSFUL` before migration edits.

- [x] **Step 3: Inventory Jackson references and managed declarations**

Use the repository-wide source census and Gradle declaration search already recorded in the migration notes. Preserve the known scope of approximately 2,222 source files, 3,722 references, and 194 Gradle declarations.

- [x] **Step 4: Verify the target APIs from resolved artifacts**

The inspected APIs establish these mappings:

```text
com.fasterxml.jackson.databind.ObjectMapper -> tools.jackson.databind.ObjectMapper
com.fasterxml.jackson.databind.JavaType -> tools.jackson.databind.JavaType
com.fasterxml.jackson.databind.JsonMappingException -> tools.jackson.databind.DatabindException
com.fasterxml.jackson.databind.Module -> tools.jackson.databind.JacksonModule
com.fasterxml.jackson.databind.JsonSerializer -> tools.jackson.databind.ValueSerializer
com.fasterxml.jackson.databind.JsonDeserializer -> tools.jackson.databind.ValueDeserializer
org.springframework.boot.jackson2.autoconfigure.Jackson2ObjectMapperBuilderCustomizer -> org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
```

Spring Web 7.0.9 and Spring Data Redis 4.1.1 APIs were inspected locally; use their Jackson 3 serializer/converter types rather than inventing replacements.

---

### Task 2: Migrate The Shared Retrofit Boundary

**Files:**
- Modify: `kork/kork-retrofit/src/main/java/com/netflix/spinnaker/kork/retrofit/util/CustomConverterFactory.java`
- Modify: `kork/kork-retrofit2/src/main/java/com/netflix/spinnaker/kork/retrofit/Retrofit2ServiceFactory.java`
- Modify: `keel/keel-retrofit/src/main/kotlin/com/netflix/spinnaker/keel/retrofit/InstrumentedJacksonConverter.kt`
- Modify: `kork/kork-retrofit/kork-retrofit.gradle`
- Modify: `kork/kork-retrofit2/kork-retrofit2.gradle`
- Modify: `keel/keel-retrofit/keel-retrofit.gradle`
- Test: `kork/kork-retrofit/src/test/java/com/netflix/spinnaker/kork/retrofit/util/CustomConverterFactoryTest.java`
- Test: existing Retrofit tests under `kork/kork-retrofit/src/test` and `kork/kork-retrofit2/src/test`

**Interfaces:**
- `CustomConverterFactory.create(tools.jackson.databind.ObjectMapper)` remains the shared factory entry point.
- `Retrofit2ServiceFactory.create(Class<T>, ServiceEndpoint, tools.jackson.databind.ObjectMapper, List<Interceptor>)` uses `CustomConverterFactory` instead of `JacksonConverterFactory`.
- `InstrumentedJacksonConverter.Factory(String, tools.jackson.databind.ObjectMapper)` uses the shared factory for request serialization and keeps response parse instrumentation.
- `UnparseableResponseException` reports the Jackson 3 `JavaType` and wraps `tools.jackson.databind.DatabindException`.

- [ ] **Step 1: Write the failing behavioral test**

Add a Java JUnit test that creates a Jackson 3 `JsonMapper`, obtains request and response converters from `CustomConverterFactory`, and round-trips a payload with a parameterized `List<Payload>` type. Assert the request uses `application/json; charset=UTF-8`, the response is deserialized into the requested generic type, and a `Void` response closes its body and returns `null`.

- [ ] **Step 2: Run the focused test to verify RED**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :kork:kork-retrofit:test --tests '*CustomConverterFactoryTest'
```

Expected: compilation fails because the test supplies `tools.jackson.databind.ObjectMapper` while the production factory accepts Jackson 2, proving the test exercises the migration boundary.

- [ ] **Step 3: Implement the minimal Jackson 3 converter change**

Replace Jackson 2 imports with Jackson 3 imports, construct the default mapper with `new tools.jackson.databind.ObjectMapper()`, and keep the existing `Void`, `String`, generic response, request byte serialization, and media-type paths unchanged. Replace `JacksonConverterFactory` delegation in both Retrofit service classes with `CustomConverterFactory` request conversion.

- [ ] **Step 4: Run focused and dependent tests to verify GREEN**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :kork:kork-retrofit:test --tests '*CustomConverterFactoryTest'
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :kork:kork-retrofit:test :kork:kork-retrofit2:test :keel:keel-retrofit:test
```

Expected: focused behavior and existing Retrofit/Keel tests pass without a Jackson 2 converter on the runtime classpath.

- [ ] **Step 5: Verify dependency removal**

Run:

```bash
./gradlew :kork:kork-retrofit:dependencies --configuration runtimeClasspath
./gradlew :kork:kork-retrofit2:dependencies --configuration runtimeClasspath
```

Expected: neither runtime graph contains `com.squareup.retrofit2:converter-jackson`.

---

### Task 3: Migrate Kork Web Mapper Configuration

**Files:**
- Modify: `kork/kork-web/kork-web.gradle`
- Modify: `kork/kork-web/src/main/java/com/netflix/spinnaker/config/JacksonStreamReadConstraintsCustomizer.java`
- Modify: `kork/kork-web/src/main/java/com/netflix/spinnaker/config/Jackson2CompatibilityConfiguration.java`
- Modify: `kork/kork-web/src/main/java/com/netflix/spinnaker/config/Jackson2BuilderAnnotationIntrospector.java`
- Modify: `kork/kork-web/src/main/java/com/netflix/spinnaker/config/Jackson3PropertyOrderConfiguration.java`
- Modify: `kork/kork-web/src/test/java/com/netflix/spinnaker/config/Jackson2CompatibilityConfigurationTest.java`
- Test: `kork/kork-web/src/test/java/com/netflix/spinnaker/config/Jackson3PropertyOrderConfigurationTest.java`

**Interfaces:**
- Spring Boot `JsonMapperBuilderCustomizer` remains the bean customization extension point.
- `Jackson3PropertyOrderConfiguration` continues to apply stable property ordering to the Spring-managed Jackson 3 mapper.
- Existing Jackson 2 builder-annotation compatibility is retained only for models that still carry `com.fasterxml.jackson.databind.annotation.JsonDeserialize` / `JsonPOJOBuilder`; native Jackson 3 annotations use `tools.jackson.databind.annotation`.

- [ ] **Step 1: Add a failing native-annotation runtime test**

Extend the existing mapper test with a model using Jackson 3 builder annotations and assert that the Spring-managed mapper deserializes it, including ignored and any-setter properties. Add a property-order assertion through the actual mapper rather than only checking bean registration.

- [ ] **Step 2: Run the test to verify RED**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :kork:kork-web:test --tests '*Jackson2CompatibilityConfigurationTest' --tests '*Jackson3PropertyOrderConfigurationTest'
```

Expected: the native Jackson 3 annotation path fails before its production configuration is migrated.

- [ ] **Step 3: Migrate customizers and dependencies**

Use `tools.jackson.core.StreamReadConstraints`, `tools.jackson.databind.json.JsonMapper`, and `org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer`. Remove Jackson 2 starter/dependency declarations once no source or test in `kork-web` requires them; do not remove the annotation dependency until all legacy annotations are audited.

- [ ] **Step 4: Run Kork Web verification**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :kork:kork-web:test :kork:kork-web:compileJava
```

Expected: compatibility, property-order, MVC, WebFlux, and WebClient tests pass.

---

### Task 4: Migrate Kork Core And Custom Mapper Extensions

**Files:**
- Modify: `kork/kork-core/src/main/java/com/netflix/spinnaker/kork/jackson/ObjectMapperSubtypeConfigurer.java`
- Modify: `kork/kork-core/src/main/java/com/netflix/spinnaker/kork/jackson/NamedTypeProviderModule.java`
- Modify: `kork/kork-core/src/main/java/com/netflix/spinnaker/kork/jackson/NamedTypeProvider.java`
- Modify: `orca/orca-core/src/main/java/com/netflix/spinnaker/orca/jackson/OrcaObjectMapper.java`
- Modify: `kork/kork-docker/src/main/java/com/netflix/spinnaker/kork/docker/service/DockerRegistryClient.java`
- Modify: tests adjacent to each changed class

**Interfaces:**
- Custom modules extend Jackson 3 `JacksonModule`; serializers/deserializers use `ValueSerializer` and `ValueDeserializer` APIs.
- Subtype registration continues to use Jackson 3 `NamedType`, `JavaType`, and mapper module registration.
- `OrcaObjectMapper` remains an injected/configured mapper, not a second incompatible mapper family.

- [ ] **Step 1: Add failing round-trip tests for custom modules and subtype registration**

Exercise a real mapper with one registered subtype and one custom serializer/deserializer. Assert the serialized payload and deserialized runtime subtype, not only module metadata.

- [ ] **Step 2: Run the focused tests to verify RED**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :kork:kork-core:test :kork:kork-docker:test :orca:orca-core:test
```

Expected: compilation or runtime failures identify the Jackson 2 extension methods that must be translated.

- [ ] **Step 3: Translate extension APIs without changing behavior**

Apply the resolved Jackson 3 API mappings, including `JacksonModule.SetupContext`, `ValueSerializer`, `ValueDeserializer`, `tools.jackson.databind.exc.*`, and Jackson 3 tree/core types. Preserve subtype names, ordering, null handling, and exception messages.

- [ ] **Step 4: Run the focused tests and compile**

Run the same focused Gradle tasks and require `BUILD SUCCESSFUL` before proceeding.

---

### Task 5: Migrate Data Formats, Kotlin, Redis, And Serialization Utilities

**Files:**
- Modify: every Gradle file declaring `com.fasterxml.jackson.dataformat`, `com.fasterxml.jackson.module`, or Jackson 2 databind dependencies.
- Modify: source files importing `com.fasterxml.jackson.dataformat.*`, `com.fasterxml.jackson.module.*`, or Jackson 2 databind types.
- Modify: Redis serializer/configuration consumers under `orca/`, `front50/`, `kork/`, and `keel/`.
- Modify: `keel/keel-retrofit/keel-retrofit.gradle` test dependency.
- Test: adjacent serializer, YAML, XML, properties, CBOR, Kotlin, and Redis tests.

**Interfaces:**
- JSON uses Spring Boot's Jackson 3 mapper and `tools.jackson.dataformat` factories/mappers.
- Redis uses Spring Data Redis 4.1.1's Jackson 3 serializer classes with the injected mapper.
- Kotlin uses `tools.jackson.module:jackson-module-kotlin:3.1.5` where the artifact resolves; do not add the previously 404 `tools.jackson.datatype` artifacts without verifying their published coordinates.

- [ ] **Step 1: Add failing cache and format round-trip tests**

For each format with an in-tree consumer, add or update one real round-trip test. For Redis, serialize and deserialize a representative value through the configured serializer and assert the result type and fields.

- [ ] **Step 2: Run focused tests to verify RED**

Run the owning module tests. Expected failures must be Jackson 2 type/dependency incompatibilities or serializer runtime failures, not malformed test setup.

- [ ] **Step 3: Migrate dependencies and imports**

Replace Jackson 2 coordinates and imports with resolved Jackson 3 coordinates and packages. For annotations, use the Jackson 3-required annotation package. Keep YAML/XML/properties/CBOR behavior and Kotlin null/default handling unchanged.

- [ ] **Step 4: Run format and serializer verification**

Run the affected module tests, then:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :kork:kork-web:test :orca:test :front50:test :keel:test
```

Expected: all changed serialization paths pass with no Jackson 2 databind runtime dependency.

---

### Task 6: Consolidate AWS Jackson Configuration

**Files:**
- Modify: `kork/kork-aws/kork-aws.gradle`
- Modify: AWS Jackson sources and tests under `kork/kork-aws/src`
- Modify: `clouddriver/clouddriver-aws/src/main/groovy/com/netflix/spinnaker/config/AwsConfiguration.groovy`
- Modify: duplicate AWS Jackson sources/tests under `clouddriver/clouddriver-aws/src`
- Modify: `kork/spinnaker-dependencies/spinnaker-dependencies.gradle`

**Interfaces:**
- The Kork AWS Jackson 3 module is the shared implementation; clouddriver must consume the shared bean rather than shadow it with a duplicate.
- AWS SDK v2 model serialization continues to register the existing module exactly once.
- Remove `com.netflix.awsobjectmapper:awsobjectmapper` and Jackson 2 `AmazonObjectMapperConfigurer` only after the clouddriver runtime test proves equivalent mapper behavior.

- [ ] **Step 1: Add a failing AWS mapper integration test**

Serialize and deserialize representative AWS SDK v2 model data through the Spring application context, asserting the shared module is registered and the duplicate clouddriver module is not selected.

- [ ] **Step 2: Run the test to verify RED**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :kork:kork-aws:test :clouddriver:clouddriver-aws:test
```

Expected: the test fails because the current clouddriver path still depends on Jackson 2 AWS configuration or duplicate module wiring.

- [ ] **Step 3: Consolidate on the Kork Jackson 3 module**

Migrate the AWS configuration to the injected Jackson 3 mapper and shared Kork module. Delete only duplicate implementation files proven unused by the dependency graph; retain public configuration behavior and AWS SDK v2 subtype handling.

- [ ] **Step 4: Run AWS verification**

Run the focused AWS tests and compile tasks again. Expected: `BUILD SUCCESSFUL` with no `com.netflix.awsobjectmapper` or Jackson 2 databind dependency on the relevant runtime graphs.

---

### Task 7: Migrate Remaining Service Consumers And Gradle Graphs

**Files:**
- Modify: Jackson consumers under `clouddriver/`, `orca/`, `keel/`, `gate/`, `echo/`, `fiat/`, `front50/`, `igor/`, `kayenta/`, `rosco/`, and `kork/` identified by the census.
- Modify: all affected `*.gradle` files and shared dependency constraints.
- Test: existing tests adjacent to each migrated consumer; add Java/JUnit 5 regression tests only where runtime behavior lacks coverage.

**Interfaces:**
- Every service receives Jackson 3 through the shared Spring/Kork dependency path.
- No service constructs or exposes a Jackson 2 `ObjectMapper`, `JavaType`, `JsonNode`, serializer, deserializer, or Retrofit converter.
- Public serialized field names, polymorphic type ids, date/time formats, YAML/XML output, and error responses remain unchanged unless a test documents an intentional Jackson 3 difference.

- [ ] **Step 1: Create a compile-driven failing slice per composite build**

Run the owning composite compile after each bounded group of import/dependency edits. Treat unresolved Jackson 2 symbols as the expected RED signal, then add a runtime test when the compiler cannot detect behavior changes.

- [ ] **Step 2: Replace consumer imports and declarations**

Use the Jackson 3 package/API map from Task 1. Remove direct Jackson 2 dependencies instead of relying on accidental transitives. Preserve annotation compatibility only at explicitly identified model boundaries.

- [ ] **Step 3: Run service tests by dependency fan-out**

Use `.github/dependencies.yml` to select downstream checks for changed shared modules, then run the narrowest affected service tests before broadening.

- [ ] **Step 4: Prove the repository census is clean**

Run searches for Jackson 2 databind/core/dataformat/module imports and `converter-jackson` declarations. The only remaining `com.fasterxml.jackson` references must be verified annotation-package uses or explicitly documented external API boundaries.

---

### Task 8: Final Verification And Migration Review

**Files:**
- Inspect: all modified source and Gradle files.
- Modify: `docs/superpowers/plans/2026-09-13-jackson-3-migration.md` only if execution notes need recording.

**Interfaces:**
- The final branch contains one coherent Jackson 3 migration with no unrelated Spring Boot baseline changes.

- [ ] **Step 1: Apply and verify formatting**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew spotlessApply
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew spotlessCheck
```

- [ ] **Step 2: Run affected and aggregate backend checks**

Run the changed composite build tests and then:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew test
```

Expected: `BUILD SUCCESSFUL`, with any unrelated pre-existing failure recorded separately rather than weakened.

- [ ] **Step 3: Inspect dependency graphs and source census**

Confirm no Jackson 2 runtime artifacts, Retrofit Jackson 2 converter, or duplicate AWS module remains in the migrated graphs. Confirm Jackson 3 Kotlin/dataformat coordinates resolve from the repository's configured repositories.

- [ ] **Step 4: Inspect the final diff**

Run:

```bash
```

Check for unrelated churn, debug output, missing license headers, dropped extension points, unverified defaults, and missing behavioral coverage.

- [ ] **Step 5: Record delivery state**

Report exact verification commands and outcomes, the dependency on #7996, unresolved artifact/documentation limitations, and the fact that pushing or PR creation still requires explicit permission.

## Coverage Review

- Spring Boot 4.1/Jackson 3 mapper configuration: Task 3.
- Shared Retrofit request/response conversion and instrumentation: Task 2.
- Kork custom modules, subtype registration, and service mappers: Task 4.
- YAML/XML/properties/CBOR, Kotlin, Redis, and cache round trips: Task 5.
- AWS SDK v2 module reuse and Jackson 2 AWS removal: Task 6.
- Remaining service imports, Gradle declarations, and runtime consumers: Task 7.
- Formatting, dependency absence, aggregate tests, and final diff review: Task 8.

Known external verification limits are explicitly retained: the attempted Jackson migration-guide URLs and `https://square.github.io/retrofit/` returned HTTP 404, and the Jackson 3 JSR310/JDK8 artifact coordinates previously queried returned HTTP 404. Local resolved jars and Spring APIs are the source of truth until published coordinates are confirmed.
