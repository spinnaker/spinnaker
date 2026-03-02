/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentScheduler;
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider;
import com.netflix.spinnaker.cats.cluster.NodeStatusProvider;
import com.netflix.spinnaker.cats.cluster.ShardingFilter;
import com.netflix.spinnaker.cats.redis.cluster.ClusteredAgentScheduler;
import com.netflix.spinnaker.clouddriver.core.RedisConfigurationProperties;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.env.MockEnvironment;

class AgentSchedulerConfigTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              UserConfigurations.of(AgentSchedulerConfig.class, SchedulerTestConfiguration.class))
          .withPropertyValues(
              "caching.write-enabled=true", "redis.enabled=true", "redis.scheduler.enabled=true");

  @Test
  void defaultsSchedulerTypeWhenUnset() {
    MockEnvironment environment = new MockEnvironment();

    String resolved = new AgentSchedulerConfig().redisSchedulerTypeValidation(environment);
    assertThat(resolved).isEqualTo("default");
  }

  @Test
  void defaultsSchedulerTypeWhenBlank() {
    MockEnvironment environment = new MockEnvironment().withProperty("redis.scheduler.type", "   ");

    String resolved = new AgentSchedulerConfig().redisSchedulerTypeValidation(environment);
    assertThat(resolved).isEqualTo("default");
  }

  @Test
  void acceptsSupportedSchedulerType() {
    MockEnvironment environment =
        new MockEnvironment().withProperty("redis.scheduler.type", "priority");

    String resolved = new AgentSchedulerConfig().redisSchedulerTypeValidation(environment);
    assertThat(resolved).isEqualTo("priority");
  }

  @Test
  void rejectsLegacyScalarSchedulerProperty() {
    MockEnvironment environment =
        new MockEnvironment()
            .withProperty("redis.scheduler.type", "default")
            .withProperty("redis.scheduler", "priority");

    assertThatThrownBy(() -> new AgentSchedulerConfig().redisSchedulerTypeValidation(environment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Legacy scalar 'redis.scheduler' is not supported");
  }

  @Test
  void rejectsLegacyScalarSchedulerPropertyWhenTypeUnset() {
    MockEnvironment environment = new MockEnvironment().withProperty("redis.scheduler", "priority");

    assertThatThrownBy(() -> new AgentSchedulerConfig().redisSchedulerTypeValidation(environment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Legacy scalar 'redis.scheduler' is not supported");
  }

  @Test
  void rejectsLegacyParallelismProperty() {
    MockEnvironment environment =
        new MockEnvironment()
            .withProperty("redis.scheduler.type", "sort")
            .withProperty("redis.parallelism", "9");

    assertThatThrownBy(() -> new AgentSchedulerConfig().redisSchedulerTypeValidation(environment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Legacy 'redis.parallelism' is not supported");
  }

  @Test
  void rejectsLegacyParallelismPropertyWhenTypeUnset() {
    MockEnvironment environment = new MockEnvironment().withProperty("redis.parallelism", "9");

    assertThatThrownBy(() -> new AgentSchedulerConfig().redisSchedulerTypeValidation(environment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Legacy 'redis.parallelism' is not supported");
  }

  @Test
  void rejectsUnknownSchedulerType() {
    MockEnvironment environment =
        new MockEnvironment().withProperty("redis.scheduler.type", "invalid");

    assertThatThrownBy(() -> new AgentSchedulerConfig().redisSchedulerTypeValidation(environment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unknown redis scheduler type");
  }

  @Test
  void createsDefaultRedisSchedulerBeanWhenTypeUnset() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(AgentScheduler.class);
          assertThat(context).hasBean("defaultRedisAgentScheduler");
          assertThat(context).doesNotHaveBean("sortRedisAgentScheduler");
          assertThat(context).doesNotHaveBean("priorityRedisAgentScheduler");
          assertThat(context.getBean(AgentScheduler.class))
              .isInstanceOf(ClusteredAgentScheduler.class);
        });
  }

  @Test
  void createsDefaultRedisSchedulerBeanWhenTypeBlank() {
    runner
        .withPropertyValues("redis.scheduler.type=")
        .run(
            context -> {
              assertThat(context).hasSingleBean(AgentScheduler.class);
              assertThat(context).hasBean("defaultRedisAgentScheduler");
              assertThat(context).doesNotHaveBean("sortRedisAgentScheduler");
              assertThat(context).doesNotHaveBean("priorityRedisAgentScheduler");
              assertThat(context.getBean(AgentScheduler.class))
                  .isInstanceOf(ClusteredAgentScheduler.class);
            });
  }

  static class SchedulerTestConfiguration {
    @Bean
    RedisConfigurationProperties redisConfigurationProperties() {
      RedisConfigurationProperties props = new RedisConfigurationProperties();
      props.setConnection("redis://localhost:6379");
      return props;
    }

    @Bean
    RedisClientDelegate redisClientDelegate() {
      return mock(RedisClientDelegate.class);
    }

    @Bean
    AgentIntervalProvider agentIntervalProvider() {
      return mock(AgentIntervalProvider.class);
    }

    @Bean
    NodeStatusProvider nodeStatusProvider() {
      return mock(NodeStatusProvider.class);
    }

    @Bean
    DynamicConfigService dynamicConfigService() {
      DynamicConfigService dynamicConfigService = mock(DynamicConfigService.class);
      org.mockito.Mockito.when(
              dynamicConfigService.getConfig(
                  org.mockito.ArgumentMatchers.any(),
                  org.mockito.ArgumentMatchers.anyString(),
                  org.mockito.ArgumentMatchers.any()))
          .thenAnswer(invocation -> invocation.getArgument(2));
      return dynamicConfigService;
    }

    @Bean
    ShardingFilter shardingFilter() {
      return mock(ShardingFilter.class);
    }

    @Bean
    Registry registry() {
      return new DefaultRegistry();
    }

    @Bean
    HealthEndpoint healthEndpoint() {
      return mock(HealthEndpoint.class);
    }
  }
}
