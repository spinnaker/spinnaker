/*
 * Copyright 2022 Netflix, Inc.
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

package com.netflix.spinnaker.orca;

import com.netflix.spinnaker.orca.notifications.AlwaysUnlockedNotificationClusterLock;
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.persistence.InMemoryExecutionRepository;
import com.netflix.spinnaker.q.memory.InMemoryQueue;
import com.netflix.spinnaker.q.metrics.EventPublisher;
import com.netflix.spinnaker.q.metrics.MonitorableQueue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class MeterRegistryProcessorTestConfiguration {

  @Bean
  NotificationClusterLock notificationClusterLock() {
    return new AlwaysUnlockedNotificationClusterLock();
  }

  @Bean
  ExecutionRepository executionRepository() {
    return new InMemoryExecutionRepository();
  }

  @Bean
  @Primary
  MonitorableQueue queue(Clock clock, EventPublisher publisher) {
    return new InMemoryQueue(
        clock, Duration.ofMinutes(1), Collections.emptyList(), false, publisher);
  }

  @Bean
  MeterRegistry meterRegistry() {
    return new CompositeMeterRegistry(
        io.micrometer.core.instrument.Clock.SYSTEM,
        Collections.singleton(new SimpleMeterRegistry()));
  }

  @Bean
  BeanPostProcessor testBeanPostProcessor() {
    return new MeterRegistryProcessorIntTest.TestBeanPostProcessor();
  }
}
