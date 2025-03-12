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

package com.netflix.spinnaker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import com.netflix.spinnaker.kork.annotations.Beta;
import com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer.SubtypeLocator;
import com.netflix.spinnaker.kork.plugins.remote.RemotePluginConfigChangedListener;
import com.netflix.spinnaker.kork.plugins.remote.RemotePluginsCache;
import com.netflix.spinnaker.kork.plugins.remote.RemotePluginsProvider;
import com.netflix.spinnaker.kork.plugins.remote.extension.RemoteExtensionPointDefinition;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Remote plugin beans are not statically loaded as they do not need to be loaded as early in the
 * Spring application lifecycle as beans from {@link PluginsAutoConfiguration}. Remote plugins use
 * the plugin framework for configuration, versioning, and plugin status but are otherwise
 * completely separate from the in-process plugin framework.
 */
@Configuration
@Beta
public class RemotePluginsConfiguration {

  @Bean
  public RemotePluginsCache remotePluginsCache(
      ApplicationEventPublisher applicationEventPublisher) {
    return new RemotePluginsCache(applicationEventPublisher);
  }

  @Bean
  public RemotePluginConfigChangedListener remotePluginConfigChangedListener(
      ObjectProvider<ObjectMapper> objectMapperProvider,
      ObjectProvider<List<SubtypeLocator>> subtypeLocatorsProvider,
      ObjectProvider<OkHttpClientProvider> okHttpClientProvider,
      RemotePluginsCache remotePluginsCache,
      List<RemoteExtensionPointDefinition> remoteExtensionPointDefinitions) {
    return new RemotePluginConfigChangedListener(
        objectMapperProvider,
        subtypeLocatorsProvider,
        okHttpClientProvider,
        remotePluginsCache,
        remoteExtensionPointDefinitions);
  }

  @Bean
  public RemotePluginsProvider remotePluginProvider(RemotePluginsCache remotePluginsCache) {
    return new RemotePluginsProvider(remotePluginsCache);
  }
}
