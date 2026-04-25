/*
 * Copyright 2025 Harness, Inc.
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

/**
 * Spring configuration that wires the {@link PrioritySchedulerMetrics} bean backed by Spectator's
 * {@link com.netflix.spectator.api.Registry}.
 */
package com.netflix.spinnaker.cats.redis.cluster;

import com.netflix.spectator.api.Registry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PrioritySchedulerMetricsConfiguration {

  @Bean
  public PrioritySchedulerMetrics prioritySchedulerMetrics(Registry registry) {
    return new PrioritySchedulerMetrics(registry);
  }
}
