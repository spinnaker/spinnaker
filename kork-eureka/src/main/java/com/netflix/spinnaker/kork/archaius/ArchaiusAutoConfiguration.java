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
import com.netflix.spinnaker.kork.eureka.EurekaAutoConfiguration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.configuration.CompositeConfiguration;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@ConditionalOnProperty("archaius.enabled")
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE + 10)
@AutoConfigureBefore(EurekaAutoConfiguration.class)
public class ArchaiusAutoConfiguration {

  /**
   * This is a BeanPostProcessor only to cause early initialization before any of the beans in
   * EurekaAutoConfiguration.
   *
   * <p>We can't rely on the AutoConfiguration ordering annotations for bean instantiation ordering,
   * they only serve to ensure bean definitions are registered so Conditional annotations work as
   * expected.
   *
   * <p>Since we don't have a direct dependency between the Eureka beans and these ones, we are at
   * the mercy of the context if someone directly asks for a EurekaClient.
   */
  static class ArchaiusInitializer implements BeanPostProcessor {
    private final AbstractPollingScheduler pollingScheduler;
    private final DynamicConfiguration configurationInstance;

    public ArchaiusInitializer(
        ConfigurableApplicationContext applicationContext,
        AbstractPollingScheduler pollingScheduler,
        SpringEnvironmentPolledConfigurationSource polledConfigurationSource) {
      Objects.requireNonNull(applicationContext, "applicationContext");
      this.pollingScheduler = Objects.requireNonNull(pollingScheduler, "pollingScheduler");
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

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName)
        throws BeansException {
      return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName)
        throws BeansException {
      return bean;
    }
  }

  @Bean
  public static AbstractPollingScheduler pollingScheduler(
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
  public static SpringEnvironmentPolledConfigurationSource polledConfigurationSource(
      ConfigurableApplicationContext applicationContext) {
    return new SpringEnvironmentPolledConfigurationSource(applicationContext.getEnvironment());
  }

  @Bean
  public static ArchaiusInitializer archaiusInitializer(
      ConfigurableApplicationContext applicationContext,
      AbstractPollingScheduler pollingScheduler,
      SpringEnvironmentPolledConfigurationSource polledConfigurationSource) {
    return new ArchaiusInitializer(applicationContext, pollingScheduler, polledConfigurationSource);
  }
}
