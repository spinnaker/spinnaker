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
 */
package com.netflix.spinnaker.front50.config;

import com.netflix.spinnaker.front50.config.annotations.ConditionalOnAnyProviderExceptRedisIsEnabled;
import com.netflix.spinnaker.front50.model.plugins.PluginInfoRepository;
import com.netflix.spinnaker.front50.model.plugins.PluginVersionCleanupAgent;
import com.netflix.spinnaker.front50.model.plugins.PluginVersionPinningRepository;
import com.netflix.spinnaker.front50.model.plugins.PluginVersionPinningService;
import com.netflix.spinnaker.moniker.Namer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;

@Configuration
@EnableConfigurationProperties(PluginVersionCleanupProperties.class)
@ConditionalOnAnyProviderExceptRedisIsEnabled
public class PluginVersioningConfiguration {

  @Bean
  PluginVersionPinningService pluginVersionPinningService(
      PluginVersionPinningRepository pluginVersionPinningRepository,
      PluginInfoRepository pluginInfoRepository) {
    return new PluginVersionPinningService(pluginVersionPinningRepository, pluginInfoRepository);
  }

  @Bean
  PluginVersionCleanupAgent pluginVersionCleanupAgent(
      PluginVersionPinningRepository repository,
      PluginVersionCleanupProperties properties,
      Namer<?> namer,
      TaskScheduler taskScheduler) {
    return new PluginVersionCleanupAgent(repository, properties, namer, taskScheduler);
  }
}
