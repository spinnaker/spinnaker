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

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.controllers.MetricsController;
import com.netflix.spectator.gc.GcLogger;
import com.netflix.spectator.jvm.Jmx;

import org.springframework.boot.actuate.metrics.writer.CompositeMetricWriter;
import org.springframework.boot.actuate.metrics.writer.MetricWriter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.annotation.PreDestroy;
import java.util.List;

@Configuration
@ConditionalOnClass(Registry.class)
@EnableConfigurationProperties(SpectatorGcLoggingConfiguration.class)
public class SpectatorConfiguration {

  @Bean
  @ConditionalOnMissingBean(Registry.class)
  Registry registry() {
    return new DefaultRegistry(Clock.SYSTEM);
  }

  @Bean
  MetricWriter spectatorMetricWriter(Registry registry) {
    return new SpectatorMetricWriter(registry);
  }

  @Bean
  @ConditionalOnProperty("spectator.webEndpoint.enabled")
  MetricsController metricsController(Registry registry) {
    return new MetricsController();
  }

  @Bean
  @Primary
  @ConditionalOnMissingClass("org.springframework.messaging.MessageChannel")
  @ConditionalOnMissingBean(name = "primaryMetricWriter")
  public MetricWriter primaryMetricWriter(List<MetricWriter> writers) {
    return new CompositeMetricWriter(writers);
  }

  @Bean
  RegistryInitializer registryInitializer(Registry registry, SpectatorGcLoggingConfiguration spectatorConfigurationProperties) {
    return new RegistryInitializer(registry, spectatorConfigurationProperties.isLoggingEnabled());
  }

  private static class RegistryInitializer {
    private final Registry registry;
    private final GcLogger gcLogger;

    public RegistryInitializer(Registry registry, boolean enableJmxLogging) {
      this.registry = registry;
      Spectator.globalRegistry().add(registry);
      if (enableJmxLogging) {
        Jmx.registerStandardMXBeans(registry);
      }
      gcLogger = new GcLogger();
      gcLogger.start(null);
    }

    @PreDestroy
    public void destroy() {
      gcLogger.stop();
      Spectator.globalRegistry().remove(registry);
    }
  }
}
