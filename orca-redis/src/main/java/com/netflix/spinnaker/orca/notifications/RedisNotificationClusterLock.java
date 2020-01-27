/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.orca.notifications;

import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import com.netflix.spinnaker.kork.jedis.RedisClientSelector;
import javax.annotation.Nonnull;
import redis.clients.jedis.params.SetParams;

public class RedisNotificationClusterLock implements NotificationClusterLock {

  private final RedisClientDelegate redisClientDelegate;

  public RedisNotificationClusterLock(RedisClientSelector redisClientSelector) {
    this.redisClientDelegate = redisClientSelector.primary("default");
  }

  @Override
  public boolean tryAcquireLock(@Nonnull String notificationType, long lockTimeoutSeconds) {
    String key = "lock:" + notificationType;
    return redisClientDelegate.withCommandsClient(
        client -> {
          return "OK"
              .equals(
                  client
                      // assuming lockTimeoutSeconds will be < 2147483647
                      .set(
                      key,
                      "\uD83D\uDD12",
                      SetParams.setParams().nx().ex((int) lockTimeoutSeconds)));
        });
  }
}
