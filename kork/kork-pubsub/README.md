# Kork Pubsub

This module enables config-driven Pub/Sub support for multiple underlying systems.

### Overview

To enable Pub/Sub support, client code needs to:

1. `@Import(PubsubConfig.class)` from a `@Configuration` class
1. import the system-specific config class that is going to be used, e.g. `@Import(AmazonPubsubConfig.class)` from `kork-pubsub-aws`
1. enable pubsub properties (see example below) and define one or more subscriptions
1. provide system-specific message handler beans, e.g. an implementation of `AmazonPubsubMessageHandlerFactory`
1. for each subscription defined in the Spring configuration, kork will:
    1. create the topic and queue if they don't exist already, as well as subscribe the queue to the topic
    1. call the injected message handler factory to create a message handler
    1. start a new thread to poll the queue for messages
    1. for every message that is received, invoke the message handler
    1. acknowledge the message if the message handler was successful (did not throw)


### Caveats

* Google Pub/Sub still needs to be migrated from echo
* message deduplication is not supported yet, message handlers needs to be either idempotent or implement their own message deduplication mechanism


## Operating notes

Consider this reference `pubsub` profile:

```yaml
---
spring:
  profiles: pubsub

pubsub:
  enabled: true
  amazon:
    enabled: true
    subscriptions:
    - name: mySubscription
      topicARN: arn:aws:sns:us-east-2:123456789012:MyTopic-${environment}
      queueARN: arn:aws:sns:us-east-2:123456789012:MyQueue-${environment}
      sqsMessageRetentionPeriodSeconds: 60
      maxNumberOfMessages: 10
```

### Configuration

| Parameter | Default | Notes |
|-----------|---------|-------|
| `pubsub.enabled`                 | `false`    | used to globally enable or disable pubsub |
| `pubsub.${system}.enabled`       | `false`    | used to enable or disable a specific pubsub system |
| `pubsub.${system}.subscriptions` | [REQUIRED] | a list of system-specific subscription properties |

For properties that are supported by a specific Pub/Sub system, see:
- [AmazonPubsubProperties.java](../kork-pubsub-aws/src/main/java/com/netflix/spinnaker/kork/pubsub/aws/config/AmazonPubsubProperties.java)


### Metrics

| Metric | Tags |  Notes |
|--------|------|-------|
| `pubsub.${system}.published`     | subscription                 | Counter of messages published successfully | 
| `pubsub.${system}.publishFailed` | subscription, exceptionClass | Counter of messages where the publish operation failed | 
| `pubsub.${system}.processed`     | subscription                 | Counter of messages processed successfully by this subscription's message handler | 
| `pubsub.${system}.failed`        | subscription, exceptionClass | Counter of messages that caused an exception in this subscription's message handler | 
| `pubsub.${system}.acked`         | subscription                 | Counter of messages that were acked successfully (should track closely with `pubsub.${system}.processed`) | 
| `pubsub.${system}.ackFailed`     | subscription, exceptionClass | Counter of messages the acknowledger failed to ack | 
| `pubsub.${system}.nacked`        | subscription                 | Counter of messages that were nacked successfully (should track closely with `pubsub.${system}.failed`) | 

If using the pubsub feature, it is recommended that you configure alerts for the error counters.


### Dynamic properties

The following dynamic properties are exposed and can be controlled at runtime via `DynamicConfigService`.

| Property | Default | Notes |
|----------|---------|-------|
| `pubsub.enabled`    | `false` | disables all pubsub |
| `pubsub.${system}.enabled` | `false` | disables this pubsub system | 
| `pubsub.${system}.${subscription}.enabled` | `false` | disables this particular subscription (i.e. turns off publication and message) | 

## Local queues and Redis Streams

`MessageChannel` extends the existing `PubsubPublisher` interface. Its subscriptions implement
`PubsubSubscriber` and `MessageAcknowledger`, so existing publisher/subscriber registries and APIs
continue to work. Redis channels and subscriptions register with `PubsubPublishers` and
`PubsubSubscribers`. Kork contains no agent, account, or Clouddriver state-management code.

| Mode | Construction | Delivery |
|------|--------------|----------|
| Local queue | `new LocalMessageChannel("work")` | Subscribers of that channel compete within one process. No Redis connection or configuration is required. |
| Distributed targeted | `redisPubsub.targeted("work", "blue")` | One competing consumer in `blue` receives each message during normal operation. Other groups have separate streams and receive none. |
| Distributed broadcast | `redisPubsub.broadcast("changes")` | Each active subscription gets every new message through its own uniquely named consumer group, even when multiple instances have the same subscriber name. |

Import `RedisPubsubConfig` in applications using Redis and supply a `StringRedisTemplate` bean,
for example via application-owned Spring Boot auto-configuration (`DataRedisAutoConfiguration`)
or a starter such as `spring-boot-starter-data-redis`. Configure the connection through
`spring.data.redis.*`. `RedisPubsubConfig` does not import Redis auto-configuration directly to
avoid activating Redis connections or health indicators in applications using local queues or
other pubsub transports.
Local queues need neither this import nor a Redis server. Local publishers can be added to the
existing registry with `publishers.putAll(List.of(channel))`.

Create a subscription with `channel.subscribe(name, batchSize, handler, errorHandler)` and call
`start()` when the application is ready. The handler receives a `PubsubMessage` with an ID, a
string payload, and immutable string attributes. Call `subscription.ack(message)` after successful
processing. `nack(message)` immediately returns local work to its queue; Redis leaves it pending
for later recovery. A thrown handler exception also leaves the message unacknowledged.
Applications own processing concurrency, backpressure, idempotency, and the recovery idle threshold.

For Redis, call `subscription.reclaim(minIdle, scanLimit)` periodically to claim abandoned pending
records. Scans paginate so a busy prefix cannot hide abandoned records farther into the pending
list. Reclaimed messages go through the same handler and acknowledgement path. Redis delivery is
at least once: a consumer can finish processing and fail before acknowledgement. Targeted
subscriptions read existing backlog from offset `0`; broadcast subscriptions start at the current
end of the stream, so new instances must load their initial state from its authoritative source.

`stop()` pauses consumption; `start()` resumes the same group. `close()` stops the subscription and
removes broadcast groups. Closing a local consumer returns its unacknowledged work to the queue;
closing a targeted consumer leaves its records available for another consumer to reclaim.
Broadcast groups left by an ungraceful process exit can be removed with Redis `XGROUP DESTROY`
after confirming that the instance has stopped. Record retention is bounded separately with
`channel.trim(maxLength)`; applications must choose a cap large enough for their recovery window.
Trimming can discard live backlog, so durable application state or an appropriate retention policy
is required when every historical record matters.

`bindTargetedStream(topic, group, streamKey)` supports an existing stream already dedicated to one
logical group. The caller must reserve that key for that group; new routes should use `targeted`
for automatic group isolation. This binding allows Clouddriver to retain `pubsub_agent_stream`,
`agent-runners`, and legacy `agentType` records across upgrades.

Run the real Redis tests with Docker available:

```bash
./gradlew :kork:kork-pubsub:test
```

The distributed tests start Redis 7.4 in Testcontainers; no distributed transport is mocked.
