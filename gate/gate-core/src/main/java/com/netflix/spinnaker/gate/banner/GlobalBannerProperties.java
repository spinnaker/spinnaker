/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.banner;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Spinnaker global banner subsystem.
 *
 * <pre>
 * global-banner:
 *   enabled: false
 *   redis-key-prefix: global-banner
 *   refresh-interval-ms: 60000
 *   max-message-length: 2000
 * </pre>
 */
@Data
@ConfigurationProperties("global-banner")
public class GlobalBannerProperties {

  /** Master switch — no beans are registered unless this is {@code true}. */
  private boolean enabled = false;

  /**
   * Redis key namespace prefix (so Gate can share a Redis instance with other services without
   * collisions).
   */
  private String redisKeyPrefix = "global-banner";

  /**
   * How often (milliseconds) the in-process active-banner cache is refreshed from Redis. Decouples
   * the read hot-path from Redis latency.
   */
  private long refreshIntervalMs = 60_000;

  /** Maximum permitted length of a banner message, in characters. Enforced on create/update. */
  private int maxMessageLength = 2000;
}
