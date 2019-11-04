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

import com.netflix.spinnaker.kork.plugins.ExtensionBeanDefinitionRegistryPostProcessor;
import com.netflix.spinnaker.kork.plugins.ExtensionsInjector;
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginManager;
import com.netflix.spinnaker.kork.plugins.SpringPluginStatusProvider;
import java.nio.file.Paths;
import org.pf4j.PluginStatusProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties(PluginsConfigurationProperties.class)
public class PluginsAutoConfiguration {

  @Bean
  public static PluginStatusProvider pluginStatusProvider(Environment environment) {
    return new SpringPluginStatusProvider(environment);
  }

  @Bean
  public static SpinnakerPluginManager pluginManager(
      PluginStatusProvider pluginStatusProvider, PluginsConfigurationProperties properties) {
    return new SpinnakerPluginManager(pluginStatusProvider, Paths.get(properties.rootPath));
  }

  @Bean
  public static ExtensionsInjector extensionsInjector(SpinnakerPluginManager pluginManager) {
    return new ExtensionsInjector(pluginManager);
  }

  @Bean
  public static ExtensionBeanDefinitionRegistryPostProcessor pluginBeanPostProcessor(
      SpinnakerPluginManager pluginManager, ExtensionsInjector injector) {
    return new ExtensionBeanDefinitionRegistryPostProcessor(pluginManager, injector);
  }
}
