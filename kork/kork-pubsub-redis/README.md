# Kork Pubsub Redis

Redis-backed implementations of `kork-pubsub`'s two delivery semantics:

* **Single delivery** (competing-consumer, exactly-once-across-a-fleet) - backed by Redis Streams
  and a consumer group shared by every instance. Implements the existing `PubsubPublisher`/
  `PubsubSubscriber`/`MessageAcknowledger` contract from `kork-pubsub`; no new interfaces needed.
* **Broadcast** (fan-out to every listening instance) - backed by native Redis Pub/Sub via Spring
  Integration's `RedisInboundChannelAdapter`/`RedisPublishingMessageHandler`. Implements the
  `PubsubBroadcastPublisher`/`PubsubBroadcastSubscriber` interfaces added to `kork-pubsub`, **and**
  the base `PubsubPublisher`/`PubsubSubscriber`, so generic tooling built against either contract
  can discover it.

### Why two different Redis mechanisms for two semantics

Redis Streams consumer groups already give exactly-once-across-a-fleet delivery (this is exactly
what `clouddriver/cats/cats-pubsub` uses for scheduling caching agents), so Single delivery is
built directly on Spring Data Redis's `StreamMessageListenerContainer`, following that module's
proven pattern (consumer group creation, XPENDING/XCLAIM reclaim of abandoned records).

Broadcast has no consumer-group concept - every instance needs its own independent subscription so
Redis's native `PUBLISH`/`SUBSCRIBE` fan-out delivers to all of them. Spring Integration's Redis
Pub/Sub adapters are mature (since Spring Integration 2.1) and directly fit this, so Broadcast
builds on those rather than hand-rolled `RedisMessageListenerContainer` wiring. (Spring
Integration's Redis *Streams* support was evaluated too, for the Single side - it's reactive-only
and still has no XPENDING/XCLAIM reclaim, so it wouldn't have removed the hardest part; Single
stays on the direct Spring Data Redis implementation.)

### Delivery guarantee

Single delivery is at-least-once (a crashed consumer's unacknowledged records are reclaimed and
redelivered - see `minIdleTimeForClaim` below). Broadcast reports
`DeliveryGuarantee.AT_MOST_ONCE`: native Redis Pub/Sub has no persistence, so an instance that
isn't actively listening when a message publishes will never see it. This is intended as a
latency optimization layered on an already-correct polling/full-diff baseline (e.g. nudging every
instance to refresh a cache immediately instead of waiting for its next poll), not as a
correctness-critical delivery guarantee.

### Configuration

```yaml
pubsub:
  enabled: true
  redis:
    enabled: true
    subscriptions:
    - name: my-single-delivery-subscription
      streamMaxLength: 10000
      minIdleTimeForClaim: PT5M
    broadcasts:
    - name: my-broadcast-channel
```

| Parameter | Default | Notes |
|-----------|---------|-------|
| `pubsub.redis.subscriptions[].name` | [REQUIRED] | |
| `pubsub.redis.subscriptions[].streamKey` | `name` | Redis Streams key |
| `pubsub.redis.subscriptions[].consumerGroup` | `name` | shared by every instance |
| `pubsub.redis.subscriptions[].maxNumberOfMessages` | `10` | batch size and worker-pool concurrency |
| `pubsub.redis.subscriptions[].minIdleTimeForClaim` | `PT5M` | how long a record may sit unacknowledged before the reclaim loop claims it for redelivery |
| `pubsub.redis.subscriptions[].reclaimIntervalSeconds` | `60` | |
| `pubsub.redis.subscriptions[].streamMaxLength` | `10000` | approximate cap, trimmed on every publish |
| `pubsub.redis.broadcasts[].name` | [REQUIRED] | |
| `pubsub.redis.broadcasts[].channel` | `name` | Redis Pub/Sub channel |

### Metrics

| Metric | Tags | Notes |
|--------|------|-------|
| `pubsub.redis.published` / `publishFailed` | subscription(, exceptionClass) | Single delivery publish |
| `pubsub.redis.processed` / `failed` | subscription(, exceptionClass) | Single delivery message handler outcome |
| `pubsub.redis.acked` / `ackFailed` / `nacked` | subscription(, exceptionClass) | Single delivery acknowledgement |
| `pubsub.redis.reclaimed` | subscription | Records reclaimed from a dead/stalled consumer |
| `pubsub.redis.stream.poll.errors` | subscription | Errors polling the stream itself |
| `pubsub.redis.broadcastPublished` / `broadcastPublishFailed` | subscription(, exceptionClass) | Broadcast publish |
| `pubsub.redis.broadcastProcessed` / `broadcastFailed` | subscription(, exceptionClass) | Broadcast message handler outcome |

### Known limitations

* Broadcast `publish(message, attributes)`'s `attributes` are silently ignored - a native Redis
  `PUBLISH` has no side channel for them.
* No dead-letter/max-retry policy: an unprocessable Single-delivery record is reclaimed forever
  (matching `kork-pubsub-aws`'s own lack of DLQ handling today, not a regression).
* Redis's `XCLAIM` has "last-claim-wins" semantics, unlike SQS's single visibility timeout - tune
  `minIdleTimeForClaim` well above realistic handler runtime to avoid a narrow window where two
  consumers could both believe they own a record.
