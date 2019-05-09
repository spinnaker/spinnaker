/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.cache;

import com.netflix.spinnaker.cats.agent.AgentScheduler;
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider;
import com.netflix.spinnaker.cats.cluster.DefaultNodeIdentity;
import com.netflix.spinnaker.cats.cluster.NodeStatusProvider;
import com.netflix.spinnaker.cats.dynomite.cluster.DynoClusteredAgentScheduler;
import com.netflix.spinnaker.cats.dynomite.cluster.DynoClusteredSortAgentScheduler;
import com.netflix.spinnaker.cats.redis.cluster.ClusteredAgentScheduler;
import com.netflix.spinnaker.cats.redis.cluster.ClusteredSortAgentScheduler;
import com.netflix.spinnaker.clouddriver.core.RedisConfigurationProperties;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.dynomite.DynomiteClientDelegate;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;

import java.net.URI;
import java.time.Clock;

@Configuration
@ConditionalOnProperty(value = "caching.write-enabled", matchIfMissing = true)
public class AgentSchedulerConfig {

  @Bean
  @ConditionalOnExpression("${redis.enabled:true} && ${redis.scheduler.enabled:true}")
  AgentScheduler redisAgentScheduler(RedisConfigurationProperties redisConfigurationProperties,
                                RedisClientDelegate redisClientDelegate,
                                JedisPool jedisPool,
                                AgentIntervalProvider agentIntervalProvider,
                                NodeStatusProvider nodeStatusProvider,
                                DynamicConfigService dynamicConfigService) {
    if (redisConfigurationProperties.getScheduler().equalsIgnoreCase("default")) {
      URI redisUri = URI.create(redisConfigurationProperties.getConnection());
      String redisHost = redisUri.getHost();
      int redisPort = redisUri.getPort();
      if (redisPort == -1) {
        redisPort = 6379;
      }
      return new ClusteredAgentScheduler(
        redisClientDelegate,
        new DefaultNodeIdentity(redisHost, redisPort),
        agentIntervalProvider,
        nodeStatusProvider,
        redisConfigurationProperties.getAgent().getEnabledPattern(),
        redisConfigurationProperties.getAgent().getAgentLockAcquisitionIntervalSeconds(),
        dynamicConfigService
      );
    } else if (redisConfigurationProperties.getScheduler().equalsIgnoreCase("sort")) {
      return new ClusteredSortAgentScheduler(
        jedisPool,
        nodeStatusProvider,
        agentIntervalProvider,
        redisConfigurationProperties.getParallelism()
      );
    } else {
      throw new IllegalStateException("redis.scheduler must be one of 'default', 'sort', or ''.");
    }
  }

  @Bean
  @ConditionalOnExpression("${dynomite.enabled:false} && ${dynomite.scheduler.enabled:false}")
  AgentScheduler dynomiteAgentScheduler(Clock clock,
                                RedisConfigurationProperties redisConfigurationProperties,
                                RedisClientDelegate redisClientDelegate,
                                AgentIntervalProvider agentIntervalProvider,
                                NodeStatusProvider nodeStatusProvider) {
    if (redisConfigurationProperties.getScheduler().equalsIgnoreCase("default")) {
      return new DynoClusteredAgentScheduler(
        (DynomiteClientDelegate) redisClientDelegate,
        new DefaultNodeIdentity(),
        agentIntervalProvider,
        nodeStatusProvider
      );
    } else if (redisConfigurationProperties.getScheduler().equalsIgnoreCase("sort")) {
      return new DynoClusteredSortAgentScheduler(
        clock,
        redisClientDelegate,
        nodeStatusProvider,
        agentIntervalProvider,
        redisConfigurationProperties.getParallelism()
      );
    } else {
      throw new IllegalStateException("redis.scheduler must be one of 'default', 'sort', or ''.");
    }
  }
}
