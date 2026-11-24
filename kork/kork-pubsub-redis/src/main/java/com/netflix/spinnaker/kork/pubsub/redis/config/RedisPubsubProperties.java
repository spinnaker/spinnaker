/*
 * Copyright 2026 McIntosh.farm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.pubsub.redis.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "pubsub.redis")
public class RedisPubsubProperties {
  /**
   * Competing-consumer ("single delivery") subscriptions, backed by a Redis Streams consumer group.
   */
  @Valid private List<RedisStreamSubscription> subscriptions = Collections.emptyList();

  /** Broadcast subscriptions, backed by native Redis Pub/Sub. */
  @Valid private List<RedisBroadcastSubscription> broadcasts = Collections.emptyList();

  /**
   * Subscription names key the per-subscription workers, handlers, and metrics, so a duplicate
   * would silently overwrite its twin.
   */
  @AssertTrue(message = "pubsub.redis.subscriptions[].name must be unique")
  public boolean isSubscriptionNamesUnique() {
    return subscriptions.stream()
            .map(RedisStreamSubscription::getName)
            .filter(Objects::nonNull)
            .distinct()
            .count()
        == subscriptions.stream()
            .map(RedisStreamSubscription::getName)
            .filter(Objects::nonNull)
            .count();
  }

  @AssertTrue(message = "pubsub.redis.broadcasts[].name must be unique")
  public boolean isBroadcastNamesUnique() {
    return broadcasts.stream()
            .map(RedisBroadcastSubscription::getName)
            .filter(Objects::nonNull)
            .distinct()
            .count()
        == broadcasts.stream()
            .map(RedisBroadcastSubscription::getName)
            .filter(Objects::nonNull)
            .count();
  }

  @Data
  public static class RedisStreamSubscription {
    @NotEmpty private String name;

    /** Redis Streams key to publish/consume from. Defaults to {@link #name}. */
    private String streamKey;

    /**
     * Consumer group name, shared by every instance consuming this subscription. Defaults to {@link
     * #name}.
     */
    private String consumerGroup;

    /**
     * Maximum records read from the stream per poll, and the concurrency of the worker pool. This
     * also bounds the records this instance holds at once: polling pauses while every worker is
     * busy.
     */
    @Min(1)
    private int maxNumberOfMessages = 10;

    /**
     * How long a record may sit claimed-but-unacknowledged before the reclaim loop considers its
     * consumer dead and claims it for redelivery. Redis' XCLAIM has "last-claim-wins" semantics
     * (unlike SQS's single visibility timeout), so this should be tuned well above realistic
     * handler runtime to avoid a window where two consumers could both believe they own a record.
     */
    @NotNull private Duration minIdleTimeForClaim = Duration.ofMinutes(5);

    @Min(1)
    private int reclaimIntervalSeconds = 60;

    /**
     * Approximate cap on stream length, trimmed on every publish via {@code XADD ... MAXLEN ~}.
     * Trimming ignores consumer groups, so this must comfortably exceed the number of records that
     * can be published while consumers are down or lagging; a record trimmed before it is consumed
     * is lost.
     */
    @Min(1)
    private long streamMaxLength = 10_000;

    /**
     * How many times a record may be delivered before it is considered a poison message and moved
     * to the dead-letter stream instead of being redelivered again. A record's delivery count
     * increases on every delivery, including each reclaim after a failed or abandoned attempt.
     */
    @Min(1)
    private int maxDeliveryAttempts = 5;

    /**
     * Stream that receives records which exceeded {@link #maxDeliveryAttempts}, trimmed to {@link
     * #streamMaxLength}. Defaults to the {@link #getStreamKey() stream key} plus {@code
     * .dead-letter}.
     */
    private String deadLetterStreamKey;

    public String getStreamKey() {
      return streamKey != null ? streamKey : name;
    }

    public String getConsumerGroup() {
      return consumerGroup != null ? consumerGroup : name;
    }

    public String getDeadLetterStreamKey() {
      return deadLetterStreamKey != null ? deadLetterStreamKey : getStreamKey() + ".dead-letter";
    }
  }

  @Data
  public static class RedisBroadcastSubscription {
    @NotEmpty private String name;

    /** Redis Pub/Sub channel to publish/subscribe to. Defaults to {@link #name}. */
    private String channel;

    /**
     * Messages that may wait for a handler on this instance. Native Pub/Sub is at-most-once, so
     * when the queue is full further messages are dropped (and counted) rather than buffered
     * without bound.
     */
    @Min(1)
    private int queueCapacity = 1000;

    public String getChannel() {
      return channel != null ? channel : name;
    }
  }
}
