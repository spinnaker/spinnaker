/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.config;

import static java.lang.String.format;

import com.netflix.spinnaker.kork.plugins.ExtensionBeanDefinitionRegistryPostProcessor;
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginManager;
import com.netflix.spinnaker.kork.plugins.SpringPluginStatusProvider;
import com.netflix.spinnaker.kork.plugins.config.ConfigResolver;
import com.netflix.spinnaker.kork.plugins.config.SpringEnvironmentExtensionConfigResolver;
import com.netflix.spinnaker.kork.plugins.update.PluginUpdateService;
import com.netflix.spinnaker.kork.plugins.update.SpinnakerUpdateManager;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.pf4j.PluginStatusProvider;
import org.pf4j.update.DefaultUpdateRepository;
import org.pf4j.update.UpdateManager;
import org.pf4j.update.UpdateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties(PluginsConfigurationProperties.class)
public class PluginsAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(PluginsAutoConfiguration.class);

  @Bean
  public static PluginStatusProvider pluginStatusProvider(Environment environment) {
    return new SpringPluginStatusProvider(environment);
  }

  @Bean
  @ConditionalOnMissingBean(ConfigResolver.class)
  public static ConfigResolver springEnvironmentConfigResolver(
      ConfigurableEnvironment environment) {
    return new SpringEnvironmentExtensionConfigResolver(environment);
  }

  @Bean
  public static SpinnakerPluginManager pluginManager(
      PluginStatusProvider pluginStatusProvider,
      Environment environment,
      ConfigResolver configResolver) {
    return new SpinnakerPluginManager(
        pluginStatusProvider,
        configResolver,
        Paths.get(
            environment.getProperty(
                PluginsConfigurationProperties.ROOT_PATH_CONFIG,
                PluginsConfigurationProperties.DEFAULT_ROOT_PATH)));
  }

  @Bean
  public static UpdateManager pluginUpdateManager(
      SpinnakerPluginManager pluginManager, PluginsConfigurationProperties properties) {
    // TODO(rz): If no repositories, should we setup something to default to?
    List<UpdateRepository> repositories =
        properties.repositories.entrySet().stream()
            .map(
                entry -> {
                  try {
                    return new DefaultUpdateRepository(
                        entry.getKey(), new URL(entry.getValue().url));
                  } catch (MalformedURLException e) {
                    throw new BeanCreationException(
                        format("Plugin repository '%s' has malformed URL", entry.getKey()), e);
                  }
                })
            .collect(Collectors.toList());

    return new SpinnakerUpdateManager(pluginManager, repositories);
  }

  @Bean
  public static PluginUpdateService pluginUpdateManagerAgent(
      UpdateManager updateManager, SpinnakerPluginManager pluginManager) {
    return new PluginUpdateService(updateManager, pluginManager);
  }

  @Bean
  public static ExtensionBeanDefinitionRegistryPostProcessor pluginBeanPostProcessor(
      SpinnakerPluginManager pluginManager,
      PluginUpdateService updateManagerService,
      ApplicationEventPublisher applicationEventPublisher) {
    return new ExtensionBeanDefinitionRegistryPostProcessor(
        pluginManager, updateManagerService, applicationEventPublisher);
  }
}
