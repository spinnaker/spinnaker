/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.kork.archaius;

import com.netflix.config.AbstractPollingScheduler;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicConfiguration;
import com.netflix.config.FixedDelayPollingScheduler;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.configuration.CompositeConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@ConditionalOnProperty("archaius.enabled")
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE + 10)
public class ArchaiusAutoConfiguration {

  static class ArchaiusInitializer {
    private final ConfigurableApplicationContext applicationContext;
    private final AbstractPollingScheduler pollingScheduler;
    private final SpringEnvironmentPolledConfigurationSource polledConfigurationSource;
    private final DynamicConfiguration configurationInstance;

    public ArchaiusInitializer(
        ConfigurableApplicationContext applicationContext,
        AbstractPollingScheduler pollingScheduler,
        SpringEnvironmentPolledConfigurationSource polledConfigurationSource) {
      this.applicationContext = Objects.requireNonNull(applicationContext, "applicationContext");
      this.pollingScheduler = Objects.requireNonNull(pollingScheduler, "pollingScheduler");
      this.polledConfigurationSource =
          Objects.requireNonNull(polledConfigurationSource, "polledConfigurationSource");

      DynamicConfiguration installedConfiguration = null;
      if (!ConfigurationManager.isConfigurationInstalled()) {
        installedConfiguration =
            new DynamicConfiguration(polledConfigurationSource, pollingScheduler);
        CompositeConfiguration configuration = new CompositeConfiguration();
        configuration.addConfiguration(installedConfiguration);
        ConfigurationManager.install(configuration);
      } else {
        pollingScheduler.stop();
      }
      configurationInstance = installedConfiguration;
      AbstractConfiguration config = ConfigurationManager.getConfigInstance();

      applicationContext.getBeanFactory().registerSingleton("environmentBackedConfig", config);
      applicationContext
          .getBeanFactory()
          .registerAlias("environmentBackedConfig", "abstractConfiguration");
    }

    @PreDestroy
    public void shutdown() {
      if (configurationInstance != null) {
        pollingScheduler.stop();
        ((CompositeConfiguration) ConfigurationManager.getConfigInstance())
            .removeConfiguration(configurationInstance);
      }
    }
  }

  @Bean
  public AbstractPollingScheduler pollingScheduler(
      ConfigurableApplicationContext applicationContext) {
    int initialDelayMillis =
        applicationContext
            .getEnvironment()
            .getProperty(FixedDelayPollingScheduler.INITIAL_DELAY_PROPERTY, Integer.class, 0);
    int delayMillis =
        applicationContext
            .getEnvironment()
            .getProperty(
                FixedDelayPollingScheduler.DELAY_PROPERTY,
                Integer.class,
                (int) TimeUnit.SECONDS.toMillis(15));
    return new FixedDelayPollingScheduler(initialDelayMillis, delayMillis, false);
  }

  @Bean
  public SpringEnvironmentPolledConfigurationSource polledConfigurationSource(
      ConfigurableApplicationContext applicationContext) {
    return new SpringEnvironmentPolledConfigurationSource(applicationContext.getEnvironment());
  }

  @Bean
  public ArchaiusInitializer archaiusInitializer(
      ConfigurableApplicationContext applicationContext,
      AbstractPollingScheduler pollingScheduler,
      SpringEnvironmentPolledConfigurationSource polledConfigurationSource) {
    return new ArchaiusInitializer(applicationContext, pollingScheduler, polledConfigurationSource);
  }
}
