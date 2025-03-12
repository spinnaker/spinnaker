/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.spinnaker.config;

import com.netflix.spinnaker.cats.cluster.DefaultNodeIdentity;
import com.netflix.spinnaker.cats.cluster.ShardingFilter;
import com.netflix.spinnaker.cats.redis.cluster.CachingPodsObserver;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(value = "caching.write-enabled", matchIfMissing = true)
public class RedisShardingFilterConfiguration {

  @Bean
  @ConditionalOnExpression(
      "${redis.enabled:true} && ${redis.scheduler.enabled:true} && ${cache-sharding.enabled:false}")
  ShardingFilter shardingFilter(
      RedisClientDelegate redisClientDelegate, DynamicConfigService dynamicConfigService) {
    return new CachingPodsObserver(
        redisClientDelegate, new DefaultNodeIdentity(), dynamicConfigService);
  }
}
