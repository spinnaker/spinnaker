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

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AgentSchedulerConfigTest {

  @Test
  void requiresExplicitSchedulerType() {
    MockEnvironment environment = new MockEnvironment();

    assertThatThrownBy(() -> new AgentSchedulerConfig().redisSchedulerTypeValidation(environment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("redis.scheduler.type must be explicitly set");
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
  void rejectsUnknownSchedulerType() {
    MockEnvironment environment =
        new MockEnvironment().withProperty("redis.scheduler.type", "invalid");

    assertThatThrownBy(() -> new AgentSchedulerConfig().redisSchedulerTypeValidation(environment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unknown redis scheduler type");
  }
}
