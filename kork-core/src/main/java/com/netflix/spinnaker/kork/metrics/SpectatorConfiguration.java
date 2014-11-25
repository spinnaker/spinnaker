/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.kork.metrics;

import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spectator.api.Spectator;
import org.springframework.boot.actuate.metrics.writer.CompositeMetricWriter;
import org.springframework.boot.actuate.metrics.writer.MetricWriter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

@Configuration
@ConditionalOnClass(ExtendedRegistry.class)
public class SpectatorConfiguration {

  @Bean
  @ConditionalOnMissingBean(ExtendedRegistry.class)
  ExtendedRegistry extendedRegistry() {
    return Spectator.registry();
  }

  @Bean
  MetricWriter spectatorMetricWriter(ExtendedRegistry extendedRegistry) {
    return new SpectatorMetricWriter(extendedRegistry);
  }

  @Bean
  @Primary
  @ConditionalOnMissingClass(name = "org.springframework.messaging.MessageChannel")
  @ConditionalOnMissingBean(name = "primaryMetricWriter")
  public MetricWriter primaryMetricWriter(List<MetricWriter> writers) {
    return new CompositeMetricWriter(writers);
  }
}
