/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.zombie;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import redis.clients.jedis.Jedis;
import redis.clients.util.Pool;

@Deprecated
@Configuration
@ComponentScan(basePackageClasses = ZombiePipelineCleanupConfig.class)
@EnableScheduling
public class ZombiePipelineCleanupConfig {

  @Bean
  Lock zombiePollerLock(@Qualifier("jedisPool") Pool<Jedis> redisPool) {
    return new SimpleRedisLock(
      redisPool,
      ZombiePipelineCleanupAgent.class.getSimpleName(),
      Duration.ofHours(3)
    );
  }

}
