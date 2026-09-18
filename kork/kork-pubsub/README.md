# Kork Pubsub

This module enables config-driven Pub/Sub support for multiple underlying systems.

### Overview

To enable Pub/Sub support, client code needs to:

1. `@Import(PubsubConfig.class)` from a `@Configuration` class
1. import the system-specific config class that is going to be used, e.g. `@Import(AmazonPubsubConfig.class)` from `kork-aws-pubsub`
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


### Broadcast

Alongside the competing-consumer model above (one message, one subscriber across a fleet of
instances), this module also defines a broadcast model: one message, delivered to *every*
listening instance. See `PubsubBroadcastPublisher`, `PubsubBroadcastSubscriber`, and
`PubsubBroadcastMessageHandler` in `model/`.

Broadcast's delivery guarantee is a property of the backing transport (and, for some transports,
how it's configured), not a fixed property of "broadcast" as a concept. Implementations report
this via `getDeliveryGuarantee()`:

* `AT_MOST_ONCE` (the default) - an instance that isn't actively listening when a message is
  published will never see it. This is appropriate when broadcast is used as a latency
  optimization layered on top of an already-correct baseline (e.g. nudging every instance to
  refresh a cache immediately instead of waiting for its next poll), not as the sole delivery
  mechanism.
* `AT_LEAST_ONCE` - an instance will eventually see every message published after it started
  listening, even across a brief disconnect, at the cost of possible duplicate delivery. This
  requires a transport that persists messages until every listener has consumed them (e.g. a
  separate consumer group per listening instance on a durable log), which not every
  implementation offers.

An implementation may also implement `PubsubPublisher`/`PubsubSubscriber` alongside the broadcast
interfaces, so that generic tooling built against the competing-consumer contract can still
discover it.


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
- [AmazonPubsubProperties.java](./src/main/java/com/netflix/spinnaker/kork/pubsub/aws/config/AmazonPubsubProperties.java)


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
