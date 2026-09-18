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
import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "pubsub.redis")
public class RedisPubsubProperties {
  /**
   * Competing-consumer ("single delivery") subscriptions, backed by a Redis Streams consumer group.
   */
  @Valid private List<RedisStreamSubscription> subscriptions = Collections.emptyList();

  /** Broadcast subscriptions, backed by native Redis Pub/Sub. */
  @Valid private List<RedisBroadcastSubscription> broadcasts = Collections.emptyList();

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

    /** Maximum records read from the stream per poll, and the concurrency of the worker pool. */
    private int maxNumberOfMessages = 10;

    /**
     * How long a record may sit claimed-but-unacknowledged before the reclaim loop considers its
     * consumer dead and claims it for redelivery. Redis' XCLAIM has "last-claim-wins" semantics
     * (unlike SQS's single visibility timeout), so this should be tuned well above realistic
     * handler runtime to avoid a window where two consumers could both believe they own a record.
     */
    private Duration minIdleTimeForClaim = Duration.ofMinutes(5);

    private int reclaimIntervalSeconds = 60;

    /** Approximate cap on stream length, trimmed on every publish via {@code XADD ... MAXLEN ~}. */
    private long streamMaxLength = 10_000;

    public String getStreamKey() {
      return streamKey != null ? streamKey : name;
    }

    public String getConsumerGroup() {
      return consumerGroup != null ? consumerGroup : name;
    }
  }

  @Data
  public static class RedisBroadcastSubscription {
    @NotEmpty private String name;

    /** Redis Pub/Sub channel to publish/subscribe to. Defaults to {@link #name}. */
    private String channel;

    public String getChannel() {
      return channel != null ? channel : name;
    }
  }
}
