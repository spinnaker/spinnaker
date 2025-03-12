/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.igor.plugins;

import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class RedisPluginCache implements PluginCache {

  private static final String ID = "plugins";

  private final RedisClientDelegate redisClientDelegate;
  private final IgorConfigurationProperties igorConfigurationProperties;

  public RedisPluginCache(
      RedisClientDelegate redisClientDelegate,
      IgorConfigurationProperties igorConfigurationProperties) {
    this.redisClientDelegate = redisClientDelegate;
    this.igorConfigurationProperties = igorConfigurationProperties;
  }

  private String key() {
    return igorConfigurationProperties.getSpinnaker().getJedis().getPrefix() + ":" + ID;
  }

  @Override
  public void setLastPollCycle(@Nonnull String pluginId, @Nonnull Instant timestamp) {
    redisClientDelegate.withCommandsClient(
        c -> {
          c.hset(key(), pluginId, String.valueOf(timestamp.toEpochMilli()));
        });
  }

  @Override
  @Nullable
  public Instant getLastPollCycle(@Nonnull String pluginId) {
    return redisClientDelegate.withCommandsClient(
        c -> {
          return Optional.ofNullable(c.hget(key(), pluginId))
              .map(Long::parseLong)
              .map(Instant::ofEpochMilli)
              .orElse(null);
        });
  }

  @Override
  @Nonnull
  public Map<String, Instant> listLastPollCycles() {
    return redisClientDelegate.withCommandsClient(
        c -> {
          Map<String, Instant> cycles = new HashMap<>();
          c.hgetAll(key())
              .forEach(
                  (pluginId, ts) -> cycles.put(pluginId, Instant.ofEpochMilli(Long.parseLong(ts))));
          return cycles;
        });
  }
}
