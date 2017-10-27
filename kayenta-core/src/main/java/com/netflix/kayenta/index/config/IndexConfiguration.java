/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.index.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.index.CanaryConfigIndex;
import com.netflix.kayenta.index.CanaryConfigIndexingAgent;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.storage.StorageServiceRepository;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import redis.clients.jedis.JedisPool;

@Configuration
@EnableConfigurationProperties
public class IndexConfiguration {

  @Bean
  @ConfigurationProperties("kayenta.index")
  IndexConfigurationProperties indexConfigurationProperties() {
    return new IndexConfigurationProperties();
  }

  @Bean
  CanaryConfigIndexingAgent canaryConfigCachingAgent(String currentInstanceId,
                                                     JedisPool jedisPool,
                                                     AccountCredentialsRepository accountCredentialsRepository,
                                                     StorageServiceRepository storageServiceRepository,
                                                     ObjectMapper kayentaObjectMapper,
                                                     CanaryConfigIndex canaryConfigIndex,
                                                     IndexConfigurationProperties indexConfigurationProperties) {
    return new CanaryConfigIndexingAgent(currentInstanceId,
                                         jedisPool,
                                         accountCredentialsRepository,
                                         storageServiceRepository,
                                         kayentaObjectMapper,
                                         canaryConfigIndex,
                                         indexConfigurationProperties);
  }

  @Bean
  CanaryConfigIndex canaryConfigIndex(JedisPool jedisPool,
                                      ObjectMapper kayentaObjectMapper) {
    return new CanaryConfigIndex(jedisPool, kayentaObjectMapper);
  }

  @Bean
  public ThreadPoolTaskScheduler threadPoolTaskScheduler() {
    ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
    threadPoolTaskScheduler.setPoolSize(2);
    threadPoolTaskScheduler.setThreadNamePrefix("CanaryConfigIndexingAgentThreadPoolTaskScheduler");

    return threadPoolTaskScheduler;
  }
}
