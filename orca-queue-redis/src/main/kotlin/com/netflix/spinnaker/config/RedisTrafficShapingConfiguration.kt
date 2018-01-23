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

package com.netflix.spinnaker.config

import com.netflix.spinnaker.orca.q.handler.DeadMessageHandler
import com.netflix.spinnaker.orca.q.redis.RedisPriorityCapacityRepository
import com.netflix.spinnaker.orca.q.redis.RedisQueue
import com.netflix.spinnaker.orca.q.redis.RedisRateLimitBackend
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import java.time.Clock

@Configuration
@ConditionalOnExpression("\${queue.redis.enabled:true} && \${queue.trafficShaping.enabled:false}")
class RedisTrafficShapingConfiguration {
  @Bean fun redisRateLimitBackend(
    @Qualifier("jedisPool") redisPool: Pool<Jedis>,
    clock: Clock
  ) =
    RedisRateLimitBackend(redisPool, clock)

  @Bean fun redisPriorityCapacityRepository(
    @Qualifier("jedisPool") redisPool: Pool<Jedis>,
    properties: TrafficShapingProperties.PriorityCapacityProperties
  ) =
    RedisPriorityCapacityRepository(redisPool, properties)

}
