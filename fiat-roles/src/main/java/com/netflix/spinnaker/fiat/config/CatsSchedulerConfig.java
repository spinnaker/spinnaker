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

package com.netflix.spinnaker.fiat.config;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import com.netflix.spinnaker.cats.redis.JedisSource;
import com.netflix.spinnaker.cats.redis.cluster.AgentIntervalProvider;
import com.netflix.spinnaker.cats.redis.cluster.ClusteredAgentScheduler;
import com.netflix.spinnaker.cats.redis.cluster.DefaultAgentIntervalProvider;
import com.netflix.spinnaker.cats.redis.cluster.DefaultNodeIdentity;
import com.netflix.spinnaker.cats.redis.cluster.DefaultNodeStatusProvider;
import com.netflix.spinnaker.cats.redis.cluster.NodeIdentity;
import com.netflix.spinnaker.cats.redis.cluster.NodeStatusProvider;
import com.netflix.spinnaker.fiat.roles.UserRolesSyncer;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@ConditionalOnExpression("${fiat.writeMode.enabled:true}")
public class CatsSchedulerConfig {

  @Autowired
  UserRolesSyncer userRolesSyncer;

  ClusteredAgentScheduler scheduler;

  @Value("${fiat.writeMode.syncDelayMs:600000}")
  String syncDelayMs;

  @Value("${fiat.writeMode.syncDelayTimeoutMs:30000}")
  String syncDelayTimeoutMs;

  @Bean
  NodeIdentity nodeIdentity() {
    return new DefaultNodeIdentity();
  }

  @Bean
  AgentIntervalProvider agentIntervalProvider() {
    Long pollInterval = Long.parseLong(syncDelayMs);
    Long timeout = Long.parseLong(syncDelayTimeoutMs);
    return new DefaultAgentIntervalProvider(pollInterval, pollInterval + timeout);
  }

  @Bean
  NodeStatusProvider nodeStatusProvider() {
    return new DefaultNodeStatusProvider();
  }

  @Bean
  ClusteredAgentScheduler clusteredAgentScheduler(JedisSource jedisSource,
                                                  NodeIdentity nodeIdentity,
                                                  AgentIntervalProvider intervalProvider,
                                                  NodeStatusProvider nodeStatusProvider,
                                                  ExecutionInstrumentation executionInstrumentation) {
    scheduler = new ClusteredAgentScheduler(jedisSource, nodeIdentity, intervalProvider, nodeStatusProvider);
    scheduler.schedule(userRolesSyncer, userRolesSyncer.getAgentExecution(null), executionInstrumentation);
    return scheduler;
  }

  @PreDestroy
  void cleanup() {
    scheduler.unschedule(userRolesSyncer);
  }

  @Slf4j
  @Component
  static class LoggingInstrumentation implements ExecutionInstrumentation {

    @Override
    public void executionStarted(Agent agent) {
      log.debug("{}:{} starting", agent.getProviderName(), agent.getAgentType());
    }

    @Override
    public void executionCompleted(Agent agent, long durationMs) {
      log.info("{}:{} completed in {}s", agent.getProviderName(), agent.getAgentType(), TimeUnit.MILLISECONDS.toSeconds(durationMs));
    }

    @Override
    public void executionFailed(Agent agent, Throwable cause) {
      log.warn(agent.getAgentType() + ":" + agent.getAgentType() + " failed", cause);
    }
  }
}
