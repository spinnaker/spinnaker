/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.kork.archaius;

import com.netflix.config.AbstractPollingScheduler;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicConfiguration;
import com.netflix.config.FixedDelayPollingScheduler;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import org.apache.commons.configuration.CompositeConfiguration;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePropertySource;

@Configuration
public class ArchaiusConfiguration {

  /** This is a BeanPostProcessor to ensure early initialization only. */
  static class ArchaiusInitializingBeanPostProcessor implements BeanPostProcessor, Ordered {
    private final ConfigurableApplicationContext applicationContext;
    private final AbstractPollingScheduler pollingScheduler;
    private final SpringEnvironmentPolledConfigurationSource polledConfigurationSource;
    private final List<ClasspathPropertySource> propertyBindings;
    private final DynamicConfiguration configurationInstance;

    public ArchaiusInitializingBeanPostProcessor(
        ConfigurableApplicationContext applicationContext,
        AbstractPollingScheduler pollingScheduler,
        SpringEnvironmentPolledConfigurationSource polledConfigurationSource,
        List<ClasspathPropertySource> propertyBindings) {
      this.applicationContext = Objects.requireNonNull(applicationContext, "applicationContext");
      this.pollingScheduler = Objects.requireNonNull(pollingScheduler, "pollingScheduler");
      this.polledConfigurationSource =
          Objects.requireNonNull(polledConfigurationSource, "polledConfigurationSource");
      this.propertyBindings = propertyBindings != null ? propertyBindings : Collections.emptyList();
      initPropertyBindings();

      configurationInstance = new DynamicConfiguration(polledConfigurationSource, pollingScheduler);
      if (!ConfigurationManager.isConfigurationInstalled()) {
        ConfigurationManager.install(new CompositeConfiguration());
      }
      CompositeConfiguration config =
          (CompositeConfiguration) ConfigurationManager.getConfigInstance();
      config.addConfiguration(configurationInstance);

      applicationContext
          .getBeanFactory()
          .registerSingleton("environmentBackedConfig", ConfigurationManager.getConfigInstance());
      applicationContext
          .getBeanFactory()
          .registerAlias("environmentBackedConfig", "abstractConfiguration");
    }

    @PreDestroy
    public void shutdown() {
      pollingScheduler.stop();
      ((CompositeConfiguration) ConfigurationManager.getConfigInstance())
          .removeConfiguration(configurationInstance);
    }

    private void initPropertyBindings() {
      MutablePropertySources sources = applicationContext.getEnvironment().getPropertySources();
      Set<String> activeProfiles =
          new HashSet<>(Arrays.asList(applicationContext.getEnvironment().getActiveProfiles()));
      for (ClasspathPropertySource binding : propertyBindings) {
        for (String profile : activeProfiles) {
          if (binding.supportsProfile(profile)) {
            res(binding.getBaseName(), profile).ifPresent(sources::addLast);
          }
        }
        res(binding.getBaseName(), null).ifPresent(sources::addLast);
      }
    }

    private Optional<ResourcePropertySource> res(String base, String profile) {
      String name = base;
      String res = "/" + base;
      if (profile != null && !profile.isEmpty()) {
        name += ": " + profile;
        res += "-" + profile;
      }
      res += ".properties";
      Resource r = applicationContext.getResource(res);
      if (r.exists()) {
        try {
          return Optional.of(new ResourcePropertySource(name, r));
        } catch (IOException ioe) {
          throw new SystemException("Error loading property source [" + name + "]: " + res, ioe);
        }
      }
      return Optional.empty();
    }

    @Override
    public int getOrder() {
      return Ordered.HIGHEST_PRECEDENCE + 10;
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
  static AbstractPollingScheduler pollingScheduler(
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
  static SpringEnvironmentPolledConfigurationSource polledConfigurationSource(
      ConfigurableApplicationContext applicationContext) {
    return new SpringEnvironmentPolledConfigurationSource(applicationContext.getEnvironment());
  }

  @Bean
  static ArchaiusInitializingBeanPostProcessor archaiusInitializingBeanPostProcessor(
      ConfigurableApplicationContext applicationContext,
      Optional<List<ClasspathPropertySource>> propertyBindings,
      AbstractPollingScheduler pollingScheduler,
      SpringEnvironmentPolledConfigurationSource polledConfigurationSource) {
    return new ArchaiusInitializingBeanPostProcessor(
        applicationContext,
        pollingScheduler,
        polledConfigurationSource,
        propertyBindings.orElse(Collections.emptyList()));
  }
}
