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
package com.netflix.spinnaker.igor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.history.EchoService;
import com.netflix.spinnaker.igor.plugins.PluginCache;
import com.netflix.spinnaker.igor.plugins.PluginsBuildMonitor;
import com.netflix.spinnaker.igor.plugins.RedisPluginCache;
import com.netflix.spinnaker.igor.plugins.front50.Front50Service;
import com.netflix.spinnaker.igor.plugins.front50.PluginReleaseService;
import com.netflix.spinnaker.igor.polling.LockService;
import com.netflix.spinnaker.igor.util.RetrofitUtils;
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Configuration
@ConditionalOnProperty("services.front50.base-url")
public class PluginMonitorConfig {

  @Bean
  public PluginCache pluginCache(
      RedisClientDelegate redisClientDelegate, IgorConfigurationProperties properties) {
    return new RedisPluginCache(redisClientDelegate, properties);
  }

  @Bean
  public PluginReleaseService pluginReleaseService(
      OkHttp3ClientConfiguration okHttp3ClientConfig,
      IgorConfigurationProperties properties,
      ObjectMapper objectMapper) {
    String address = properties.getServices().getFront50().getBaseUrl();

    Front50Service front50Service =
        new Retrofit.Builder()
            .baseUrl(RetrofitUtils.getBaseUrl(address))
            .client(okHttp3ClientConfig.createForRetrofit2().build())
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .addConverterFactory(JacksonConverterFactory.create(objectMapper))
            .build()
            .create(Front50Service.class);

    return new PluginReleaseService(front50Service);
  }

  @Bean
  public PluginsBuildMonitor pluginsBuildMonitor(
      IgorConfigurationProperties properties,
      Registry registry,
      DynamicConfigService dynamicConfigService,
      DiscoveryStatusListener discoveryStatusListener,
      Optional<LockService> lockService,
      PluginReleaseService pluginReleaseService,
      PluginCache pluginCache,
      Optional<EchoService> echoService,
      TaskScheduler taskScheduler) {
    return new PluginsBuildMonitor(
        properties,
        registry,
        dynamicConfigService,
        discoveryStatusListener,
        lockService,
        pluginReleaseService,
        pluginCache,
        echoService,
        taskScheduler);
  }
}
