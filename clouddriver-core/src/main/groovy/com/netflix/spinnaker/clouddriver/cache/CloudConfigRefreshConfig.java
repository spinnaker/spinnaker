/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cache;

import com.netflix.spinnaker.cats.cluster.DefaultAgentIntervalProvider;
import com.netflix.spinnaker.clouddriver.refresh.CloudConfigRefreshScheduler;
import com.netflix.spinnaker.kork.configserver.autoconfig.RemoteConfigSourceConfigured;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.annotation.*;

/**
 * Create a {@link CloudConfigRefreshScheduler} to refresh the Spring Cloud Config Server from an
 * environment repository backend on a schedule that matches the cache refresh schedule.
 */
@Configuration
@AutoConfigureAfter({RedisCacheConfig.class})
public class CloudConfigRefreshConfig {

  @Configuration
  @Conditional(RemoteConfigSourceConfigured.class)
  @EnableConfigServer
  static class RemoteConfigSourceConfiguration {
    @Bean
    @ConditionalOnBean(DefaultAgentIntervalProvider.class)
    public CloudConfigRefreshScheduler intervalProviderConfigRefreshScheduler(
        ContextRefresher contextRefresher, DefaultAgentIntervalProvider agentIntervalProvider) {
      return new CloudConfigRefreshScheduler(contextRefresher, agentIntervalProvider.getInterval());
    }

    @Bean
    @ConditionalOnMissingBean(DefaultAgentIntervalProvider.class)
    public CloudConfigRefreshScheduler defaultIntervalConfigRefreshScheduler(
        ContextRefresher contextRefresher) {
      return new CloudConfigRefreshScheduler(contextRefresher, 60);
    }
  }
}
