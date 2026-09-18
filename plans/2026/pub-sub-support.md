# Kork Pub/Sub: Broadcast + Single-delivery semantics, backed by Redis

**Status**: Planned. No phase implemented yet.

| Phase | Description | Status |
|---|---|---|
| 0 | Valkey test-infra migration (repo-wide) | Implemented on `valkeyTestInfra` branch, pending PR |
| 1 | `kork-pubsub` base additions (Broadcast interfaces + registries) | Not started |
| 2 | New `kork-pubsub-redis` module (Single via Streams, Broadcast via Spring Integration) | Not started |
| 3 | SNS/SQS broadcast support (per-instance dynamic queues) | Not started |
| 4 | clouddriver account-refresh consumer wiring | Separate follow-up, not detailed here |

Update the status table and the relevant phase section as each phase lands (link the PR, note any
deviations from the plan actually implemented).

## Context

Account refreshes in clouddriver are currently pull-only: every pod runs its own
`BasicCredentialsLoader`/`Poller` (`kork/kork-credentials/.../poller/`) against the shared SQL
`AccountDefinitionRepository` on an independent timer, with no cross-pod signaling. There's an
unimplemented TODO in `clouddriver/clouddriver-core/.../config/AccountDefinitionConfiguration.java`
(~line 202-206) referencing kork PR #958 that wanted exactly this: publish a notification when an
account changes so pods can refresh immediately instead of waiting for the next poll tick. It was
never built because kork's pub/sub abstraction (`kork-pubsub`) has no transport that can do it.
`clouddriver/cats/cats-pubsub` separately proved out Redis Streams + consumer groups for
exactly-once-across-a-fleet delivery (scheduling caching agents), but that code is clouddriver-specific.

This plan adds two general delivery semantics to kork's base pub/sub abstraction:

- **Broadcast** — a named message every pod picks up (fan-out to all listeners). New to kork.
- **Single** — a message consumed exactly once across a fleet of pods (competing-consumer). No new
  interface needed — `PubsubPublisher`/`PubsubSubscriber`/`MessageAcknowledger<S,T>` already model
  this shape (SQS/SNS and cats-pubsub's consumer group are both instantiations of it); the deliverable
  is a new Redis Streams implementation of the existing contract.

**Scope of this plan is kork** (the clouddriver account-refresh wiring that motivated it is a separate,
later PR, not detailed here). Within kork, this plan covers four phases, tracked as they're
implemented rather than delivered as one PR — see "Phases & sequencing" below.

## Design decisions from review

- **Redis client**: Spring Data Redis + Lettuce (`spring-boot-starter-data-redis`), matching
  `clouddriver/cats/cats-pubsub` — plain Jedis (kork-jedis) has no usable Streams/Pub-Sub API.
- **Test infra standard**: Valkey 8+, not Redis, for all embedded/testcontainer Redis-protocol test
  fixtures going forward (project-wide default, not just this module) — `echo/echo-integration` already
  did this (`valkey/valkey:8`); everything else in the repo still pins `library/redis:5-alpine`.
- **Reuse before build**: researched Spring Integration before designing custom transport code (see
  Phase 2 below) — adopted where it's a genuine win (Broadcast), not adopted where it doesn't remove
  the hard part (Streams reclaim).
- **Durability is a backend/config capability, not a fixed property**: Broadcast's contract must not
  hard-code "at-most-once" as an architectural given — some backends (or some configurations of a
  backend) can offer durable fan-out. Modeled via a `DeliveryGuarantee` capability (below), so future
  Kafka/RabbitMQ implementations, and even a future Redis Streams-based durable broadcast mode, can
  report `AT_LEAST_ONCE` while today's Redis native pub/sub reports `AT_MOST_ONCE`.
- **Dual interface implementation**: the Redis Broadcast implementation implements the new Broadcast
  interfaces **and** the existing `PubsubPublisher`/`PubsubSubscriber` (method shapes are compatible),
  so generic tooling/registries built against the base interfaces can still discover broadcast
  publishers/subscribers.
- **SNS/SQS broadcast**: assessed feasible (medium complexity, not "future/maybe") — planned as its
  own phase (Phase 3), not deferred indefinitely.
- **DLQ/retry**: explicitly deferred — listed under Future Enhancements, not designed here.

## Phases & sequencing

Land independently, in this order, per established preference for landing independent work before
coupled work:

0. **Valkey test-infra migration** (repo-wide, decoupled from pub/sub — land first)
1. **kork-pubsub base additions** (Broadcast interfaces + registries)
2. **kork-pubsub-redis module** (Single via Streams, Broadcast via Spring Integration's Redis adapter)
3. **SNS/SQS broadcast support** (per-instance dynamic queues) — depends on nothing above except the
   shared `InstanceIdentity` utility from Phase 1/2's cleanup
4. *(separate, later effort, not detailed here)* clouddriver account-refresh consumer wiring

---

## Phase 0: Valkey test-infra migration

Repo-wide audit found the dominant pattern is `GenericContainer` pinned to `library/redis:5-alpine`,
centralized in `kork/kork-jedis-test/src/main/java/com/netflix/spinnaker/kork/jedis/EmbeddedRedis.java:18`
(consumed by ~25+ modules transitively: front50-redis, clouddriver cats-redis/core, gate-web/core,
kayenta-core, igor-web, orca-*-redis, keiko-redis, kork-jedis itself, etc.) plus **~11 independently
hardcoded call sites** that don't route through `EmbeddedRedis` and must be edited individually:
clouddriver-integration, orca-integration, fiat-web, fiat-roles (×3 files), fiat-integration,
gate-integration, igor-integration, kayenta-integration, rosco-integration.

`echo/echo-integration/src/test/java/.../StandaloneContainerTest.java:72` already migrated to
`valkey/valkey:8` — use it as the exact reference for the image tag/config to copy everywhere else.

Work:
- Update `EmbeddedRedis.java:18`'s pinned image to `valkey/valkey:8` — fixes the ~25+ transitive
  consumers in one change.
- Update each of the ~11 independently-pinned call sites to match.
- Run the affected modules' existing test suites to confirm Valkey's Redis-protocol compatibility
  holds for each (lock manager tests, permission repository tests, etc.) — no code changes expected
  beyond the image tag, but verify rather than assume.
- New test infra added in Phase 2 (kork-pubsub-redis) uses Valkey from day one regardless of whether
  this phase has landed yet.

## Phase 1: Base additions in `kork-pubsub`

New files, package `com.netflix.spinnaker.kork.pubsub.model`:
- `BroadcastMessage.java` — transport-agnostic envelope (`channel`, `body`, `Map<String,String> attributes`).
- `DeliveryGuarantee.java` — `enum { AT_MOST_ONCE, AT_LEAST_ONCE }`.
- `PubsubBroadcastPublisher.java` — mirrors `PubsubPublisher`: `getPubsubSystem()`, `getTopicName()`,
  `getName()`, `publish(String, Map<String,String>)` (+ default no-attrs overload), and
  `default DeliveryGuarantee getDeliveryGuarantee() { return DeliveryGuarantee.AT_MOST_ONCE; }`.
- `PubsubBroadcastSubscriber.java` — mirrors `PubsubSubscriber`, same `getDeliveryGuarantee()` default.
- `PubsubBroadcastMessageHandler.java` — `void handleMessage(BroadcastMessage message)`. Document:
  exceptions are logged/counted only, never retried — nothing to redeliver in the `AT_MOST_ONCE` case;
  an `AT_LEAST_ONCE` implementation's redelivery policy is that implementation's own concern.

New files, root package `com.netflix.spinnaker.kork.pubsub`:
- `PubsubBroadcastPublishers.java`, `PubsubBroadcastSubscribers.java` — same registry shape as the
  existing `PubsubPublishers`/`PubsubSubscribers`.

Modify:
- `config/PubsubConfig.java` — add the two new registry beans. Purely additive.
- `kork-pubsub/README.md` — document Broadcast's `DeliveryGuarantee` model and that it varies by
  implementation/config, not a fixed property of "Broadcast" as a concept.

## Phase 2: New module `kork-pubsub-redis`

Register in `kork/settings.gradle` next to `"kork-pubsub-aws"`.

### Single (Redis Streams + consumer group) — build directly on Spring Data Redis, not Spring Integration

Researched Spring Integration's Redis Streams support (`ReactiveRedisStreamMessageProducer`/
`ReactiveRedisStreamMessageHandler`, since SI 5.4/6.5): it supports consumer groups, but (a) it's
**reactive-only** (Mono/Flux — no blocking adapter exists) and (b) it has **no XPENDING/XCLAIM reclaim
of abandoned messages**, confirmed against both current docs and upstream PR history, with no tracked
issue for adding it. Adopting it would add Reactor-bridging complexity to kork's largely blocking
model without removing the one genuinely hard piece — the reclaim loop would need to be hand-built
either way. So: build directly on `StringRedisTemplate`/`StreamMessageListenerContainer`
(blocking, Spring Data Redis), following `cats-pubsub`'s `PubSubAgentRunner`/`PubSubAgentScheduler`
pattern.

Package `com.netflix.spinnaker.kork.pubsub.redis.streams`:
- `RedisStreamSubscriptionInformation.java` — mirrors `AmazonSubscriptionInformation`.
- `api/RedisStreamMessageHandler.java` / `RedisStreamMessageHandlerFactory.java` — mirror
  `AmazonPubsubMessageHandler`/`Factory`.
- `api/RedisStreamMessageAcknowledger.java extends MessageAcknowledger<RedisStreamSubscriptionInformation, MapRecord<String,String,String>>`.
- `DefaultRedisStreamMessageAcknowledger.java` — `ack()` → XACK; `nack()` → no-op (matches SQS's
  "let the timeout expire" model — the reclaim loop's XCLAIM is the only redelivery path). Metrics
  `pubsub.redis.{acked,nacked}`.
- `RedisStreamsPublisher.java implements PubsubPublisher` — XADD with approximate `MAXLEN ~` trim
  built into every publish call.
- `RedisStreamsSubscriber.java implements PubsubSubscriber` — lightweight registration object, not a
  `Runnable`/own-thread (unlike AWS's `SQSSubscriber`) — `StreamMessageListenerContainer` already
  multiplexes many registrations onto one poll thread + shared worker pool.
- `RedisStreamsSubscriberProvider.java` (`@PostConstruct`) — one shared `StreamMessageListenerContainer`;
  per subscription: idempotent `ensureConsumerGroup()` (catch `BUSYGROUP`), dispatch wraps
  `messageHandlerFactory.create(subscription).handleMessage(record)` in try/catch → ack/nack, plus its
  own scheduled per-subscription XPENDING/XCLAIM reclaim loop (mirrors
  `PubSubAgentRunner.reclaimAbandonedRecords()`, generalized to N subscriptions). Consumer naming uses
  the shared `InstanceIdentity` utility (below), not a copy-paste.
- `RedisStreamsPublisherProvider.java` (`@PostConstruct`) — mirrors `SNSPublisherProvider`.

**Risk to document in the module README**: Redis's XCLAIM has "last-claim-wins" semantics (unlike
SQS's single visibility timeout) — `minIdleTimeForClaim` must be tuned well above realistic handler
runtime to avoid a double-processing window.

### Broadcast (Redis native Pub/Sub) — adopt Spring Integration's Redis adapters

Confirmed genuine win: `RedisInboundChannelAdapter` (`MessageProducer`, since SI 2.1) and
`RedisPublishingMessageHandler` (`MessageHandler`) are mature, blocking-friendly, and directly fit
fan-out semantics — a thin Spinnaker wrapper is realistic here, unlike the Streams side.

This introduces `spring-integration-redis` as a **net-new dependency family** in the monorepo (grep
confirmed zero existing usage anywhere) — flag this explicitly for reviewer sign-off; it's the
recommended default, not a foregone conclusion.

Package `com.netflix.spinnaker.kork.pubsub.redis.broadcast`:
- `api/RedisBroadcastMessageHandlerFactory.java` — returns the **base** `PubsubBroadcastMessageHandler`
  directly (no Redis-specific handler subtype needed).
- `RedisNativePubsubPublisher.java implements PubsubBroadcastPublisher, PubsubPublisher` — wraps a
  `RedisPublishingMessageHandler` per the design decision to dual-implement the base interfaces.
  Reports `DeliveryGuarantee.AT_MOST_ONCE`. Metric `pubsub.redis.broadcastPublished`.
- `RedisNativePubsubSubscriber.java implements PubsubBroadcastSubscriber, PubsubSubscriber` — wraps a
  `RedisInboundChannelAdapter`; its `MessageProducer` output channel's handler builds a
  `BroadcastMessage` and calls the registered `PubsubBroadcastMessageHandler`, logging/counting only
  on exception (no ack/nack exists).
- `RedisBroadcastProvider.java` (`@PostConstruct`) — wires one `RedisInboundChannelAdapter`/
  `RedisPublishingMessageHandler` pair per configured broadcast subscription, registers into **both**
  `PubsubBroadcastPublishers`/`Subscribers` and the existing `PubsubPublishers`/`Subscribers`.

This same `MessageProducer`/`MessageHandler`-shaped facade is what a future Kafka broadcast backend
(`spring-integration-kafka`) or RabbitMQ backend (`spring-integration-amqp`) would plug into without
redesigning the facade — both share Spring Integration's adapter model, confirmed during research.

### Shared plumbing

- `config/RedisPubsubProperties.java` — `@ConfigurationProperties(prefix = "pubsub.redis")`, two
  independent lists: `subscriptions` (Single) and `broadcasts` (Broadcast).
- `config/RedisPubsubConfig.java` — `SYSTEM = "redis"`, `@ConditionalOnProperty({"pubsub.enabled","pubsub.redis.enabled"})`,
  plain `@Import({RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class})` — same
  pattern and reason as `cats-pubsub`'s `PubSubSchedulerConfig` (bypasses a host app's
  `@EnableAutoConfiguration(exclude=...)`; safe for host apps that don't exclude it since
  `RedisAutoConfiguration`'s beans are themselves `@ConditionalOnMissingBean`).
- **Shared instance-identity extraction** (small, in-scope cleanup): `LockManager.getOwnerName()`
  (`kork-core/.../lock/LockManager.java`, ~line 53-62) and `PubSubAgentRunner.resolveConsumerName()`
  (`cats-pubsub/.../PubSubAgentRunner.java`, ~line 403-409) independently reimplement the identical
  hostname→UUID-fallback idiom. New file
  `kork/kork-core/src/main/java/com/netflix/spinnaker/kork/instance/InstanceIdentity.java` — small
  static utility (not a bean), behavior-preserving lift of that logic. Migrate both existing call
  sites to it; `kork-pubsub-redis`'s consumer/subscriber naming and Phase 3's per-instance queue naming
  use it directly, avoiding further copies. (Two more independent copies exist —
  `clouddriver-core/.../ClouddriverHostname.java`, `kork-stackdriver/.../StackdriverConfig.java` — left
  alone, out of scope, flagged only.)

### Tests

Unit tests (JUnit5 + Mockito), mirroring `SQSSubscriberTest.java`'s style: publisher XADD-args/metrics
test, ack/nack dispatch test (handler succeeds → ack once; throws → nack once, nothing else), the
reclaim loop against canned `PendingMessages`, and Broadcast publisher/subscriber tests against mocked
SI adapter beans (verify envelope construction, verify handler exceptions are only logged/counted).
Note: `cats-pubsub` itself has no unit tests for the equivalent stream/XCLAIM mechanics — this test
content is original, not ported.

Integration tests (real embedded Redis, **Valkey 8** per the Phase 0 standard, used directly in this
new module regardless of Phase 0's landing status):
- `RedisStreamsSingleDeliveryIntegrationTest` — two consumers in the same group, assert
  exactly-once-across-both; kill one mid-flight without acking, assert XCLAIM redelivers to the survivor.
- `RedisBroadcastFanoutIntegrationTest` — 3 listener registrations, publish once, assert all 3 receive
  it; attach a 4th listener *after* publishing and assert it never sees the earlier message — proves
  the `AT_MOST_ONCE` guarantee concretely rather than asserting it only in a comment.

## Phase 3: SNS/SQS broadcast support (assessed feasible — medium complexity)

Today's `AmazonPubsubProperties.subscriptions[]` is static: one pre-provisioned SQS queue per config
entry, shared by however many pods run that service — i.e. already competing-consumer/"Single"
semantics. True broadcast needs each pod to own a uniquely-named SQS queue, dynamically created at
startup and subscribed to the existing SNS topic.

Confirmed reusable as-is (already parameterized by ARN, no rework needed):
`PubSubUtils.ensureQueueExists(sqsClient, queueARN, topicARN, retentionSeconds)`,
`getQueueUrl(sqsClient, queueARN)`, `subscribeToTopic(snsClient, topicARN, queueARN)`,
`buildSQSPolicy(queueARN, topicARN)`.

Work required:
- `AmazonPubsubProperties.AmazonPubsubSubscription` — add a `broadcast: boolean` flag.
- `SQSSubscriberProvider.start()` — for broadcast subscriptions, generate a unique queue name per pod
  (using the new `InstanceIdentity` utility from Phase 2) instead of the static `queueARN`, then call
  the existing `ensureQueueExists`/`subscribeToTopic` with that generated ARN.
- **Net-new cleanup on shutdown** — no `@PreDestroy`/`DisposableBean` precedent exists anywhere in
  `kork-pubsub-aws` today (confirmed via grep); needs a new shutdown hook calling `sqsClient.deleteQueue(...)`/
  `snsClient.unsubscribe(...)`, with `sqsMessageRetentionPeriodSeconds` (already a config knob) as a
  backstop orphan-queue reaper for ungraceful pod kills. Style reference: `kork-eureka/EurekaStatusSubscriber.java`,
  `kork-jedis/lock/RedisLockManager.java`.
- IAM policy scope considerations for per-instance dynamic queue creation (broader `CreateQueue`/
  `DeleteQueue` permissions or a templated queue-name pattern) — needs a decision during implementation.
- The resulting implementation should implement the Phase 1 Broadcast interfaces too, per the
  dual-implementation decision above, reporting `DeliveryGuarantee.AT_LEAST_ONCE` (SQS's own
  redelivery-on-visibility-timeout model).

## Future Enhancements (explicitly deferred, not designed here)

- DLQ/retry handling beyond the current XCLAIM-reclaim-only (Redis Streams) and
  visibility-timeout-only (SQS) models — no max-delivery-count or dead-letter routing today, for
  either Single implementation.
- A Redis Streams-based **durable** broadcast mode (each pod as its own consumer group on a shared
  stream) as an `AT_LEAST_ONCE` alternative to native pub/sub — the `DeliveryGuarantee` capability
  added in Phase 1 already leaves room for this without an interface change later.
- Kafka/RabbitMQ backends via `spring-integration-kafka`/`spring-integration-amqp`, following the same
  facade validated in Phase 2's Broadcast design.
- Migrating `cats-pubsub` itself onto the shared `kork-pubsub-redis` module (dedup of two now-parallel
  Redis Streams implementations).
- Echo's separate, duplicate `PubsubPublisher`/`PubsubSubscriber` interfaces
  (`echo/echo-pubsub-core/.../model/`) — a known inconsistency, not touched by this plan.
- The clouddriver account-refresh consumer wiring that originally motivated this work — separate,
  later PR.

## Verification

- Phase 0: run each affected module's existing test suite after the image-tag change (no code changes
  expected).
- Phase 1/2: `./gradlew :kork-pubsub:test :kork-pubsub-redis:test`, `spotlessApply` on both.
- `./gradlew build` at the kork root — confirm `kork-pubsub-aws` is untouched and the new module
  compiles cleanly alongside it.
- Manual smoke test (Phase 2): point `RedisPubsubConfig` at a local Valkey instance, wire a trivial test
  app with one `subscriptions` entry and one `broadcasts` entry, publish by hand via `redis-cli
  XADD`/`PUBLISH`, confirm consumer-group exactly-once delivery and multi-pod fan-out behave as designed.
- Phase 3: manual smoke test against a real (or LocalStack) SNS/SQS setup with two simulated pod
  instances, confirm both receive a broadcast publish and that queue cleanup fires on shutdown.
