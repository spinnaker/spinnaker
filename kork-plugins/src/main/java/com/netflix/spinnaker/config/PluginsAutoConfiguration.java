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

import com.fasterxml.jackson.core.type.TypeReference;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.config.PluginsConfigurationProperties.PluginRepositoryProperties;
import com.netflix.spinnaker.kork.plugins.ExtensionBeanDefinitionRegistryPostProcessor;
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginManager;
import com.netflix.spinnaker.kork.plugins.SpringPluginStatusProvider;
import com.netflix.spinnaker.kork.plugins.config.ConfigResolver;
import com.netflix.spinnaker.kork.plugins.config.RepositoryConfigCoordinates;
import com.netflix.spinnaker.kork.plugins.config.SpringEnvironmentConfigResolver;
import com.netflix.spinnaker.kork.plugins.proxy.aspects.InvocationAspect;
import com.netflix.spinnaker.kork.plugins.proxy.aspects.InvocationState;
import com.netflix.spinnaker.kork.plugins.proxy.aspects.LogInvocationAspect;
import com.netflix.spinnaker.kork.plugins.proxy.aspects.MetricInvocationAspect;
import com.netflix.spinnaker.kork.plugins.spring.actuator.SpinnakerPluginEndpoint;
import com.netflix.spinnaker.kork.plugins.update.PluginUpdateService;
import com.netflix.spinnaker.kork.plugins.update.SpinnakerUpdateManager;
import com.netflix.spinnaker.kork.plugins.update.UpdateRepositoryFactory;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.pf4j.PluginStatusProvider;
import org.pf4j.update.UpdateManager;
import org.pf4j.update.UpdateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
    return new SpringEnvironmentConfigResolver(environment);
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
      SpinnakerPluginManager pluginManager, ConfigResolver configResolver) {
    Map<String, PluginRepositoryProperties> repositoriesConfig =
        configResolver.resolve(
            new RepositoryConfigCoordinates(),
            new TypeReference<HashMap<String, PluginRepositoryProperties>>() {});

    List<UpdateRepository> repositories =
        repositoriesConfig.entrySet().stream()
            .map(
                entry ->
                    new UpdateRepositoryFactory(
                            entry.getKey(),
                            entry.getValue().getUrl(),
                            entry.getValue().fileDownloader)
                        .create())
            .collect(Collectors.toList());

    if (repositories.isEmpty()) {
      log.warn(
          "No remote repositories defined, will fallback to looking for a "
              + "'repositories.json' file next to the application executable");
    }

    return new SpinnakerUpdateManager(pluginManager, repositories);
  }

  @Bean
  public static PluginUpdateService pluginUpdateManagerAgent(
      UpdateManager updateManager,
      SpinnakerPluginManager pluginManager,
      ApplicationEventPublisher applicationEventPublisher) {
    return new PluginUpdateService(updateManager, pluginManager, applicationEventPublisher);
  }

  @Bean
  public static MetricInvocationAspect metricInvocationAspect(
      ObjectProvider<Registry> registryProvider) {
    return new MetricInvocationAspect(registryProvider);
  }

  @Bean
  public static LogInvocationAspect logInvocationAspect() {
    return new LogInvocationAspect();
  }

  @Bean
  public static ExtensionBeanDefinitionRegistryPostProcessor pluginBeanPostProcessor(
      SpinnakerPluginManager pluginManager,
      PluginUpdateService updateManagerService,
      ApplicationEventPublisher applicationEventPublisher,
      List<InvocationAspect<? extends InvocationState>> invocationAspects) {
    return new ExtensionBeanDefinitionRegistryPostProcessor(
        pluginManager, updateManagerService, applicationEventPublisher, invocationAspects);
  }

  @Bean
  public static SpinnakerPluginEndpoint spinnakerPluginEndPoint(
      SpinnakerPluginManager pluginManager) {
    return new SpinnakerPluginEndpoint(pluginManager);
  }
}
