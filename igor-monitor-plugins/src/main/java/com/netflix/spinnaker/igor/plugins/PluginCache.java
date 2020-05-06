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
import jline.internal.Nullable;

/** TODO(rz): Currently only supports front50 as a repository. */
public class PluginCache {

  private static final String ID = "plugins";
  private static final String FRONT50_REPOSITORY = "front50";

  private final RedisClientDelegate redisClientDelegate;
  private final IgorConfigurationProperties igorConfigurationProperties;

  public PluginCache(
      RedisClientDelegate redisClientDelegate,
      IgorConfigurationProperties igorConfigurationProperties) {
    this.redisClientDelegate = redisClientDelegate;
    this.igorConfigurationProperties = igorConfigurationProperties;
  }

  public void setLastPollCycleTimestamp(Instant timestamp) {
    redisClientDelegate.withCommandsClient(
        c -> {
          c.hset(key(), FRONT50_REPOSITORY, String.valueOf(timestamp.toEpochMilli()));
        });
  }

  @Nullable
  public Instant getLastPollCycleTimestamp() {
    return redisClientDelegate.withCommandsClient(
        c -> {
          String timestamp = c.hget(key(), FRONT50_REPOSITORY);
          if (timestamp == null) {
            return null;
          } else {
            return Instant.ofEpochMilli(Long.parseLong(timestamp));
          }
        });
  }

  private String key() {
    return igorConfigurationProperties.getSpinnaker().getJedis().getPrefix() + ":" + ID;
  }
}
