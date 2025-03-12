/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package com.netflix.spinnaker.igor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.igor.polling.LockService;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import com.netflix.spinnaker.kork.jedis.lock.RedisLockManager;
import com.netflix.spinnaker.kork.lock.LockManager;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty("locking.enabled")
@EnableConfigurationProperties(LockManagerConfigProperties.class)
public class LockManagerConfig {
  @Autowired LockManagerConfigProperties lockManagerConfigProperties;

  @Bean
  Clock clock() {
    return Clock.systemDefaultZone();
  }

  @Bean
  LockManager redisLockManager(
      Clock clock,
      Registry registry,
      ObjectMapper mapper,
      RedisClientDelegate redisClientDelegate) {
    return new RedisLockManager(
        null, // will fall back to running node name
        clock,
        registry,
        mapper,
        redisClientDelegate,
        Optional.of(lockManagerConfigProperties.getHeartbeatRateMillis()),
        Optional.of(lockManagerConfigProperties.getLeaseDurationMillis()));
  }

  @Bean
  LockService lockService(final LockManager lockManager) {
    Duration lockMaxDuration = null;
    if (lockManagerConfigProperties.getMaximumLockDurationMillis() != null) {
      lockMaxDuration =
          Duration.ofMillis(lockManagerConfigProperties.getMaximumLockDurationMillis());
    }

    return new LockService(lockManager, lockMaxDuration);
  }
}
