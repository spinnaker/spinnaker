/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.core.Front50ConfigurationProperties;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.config.DefaultServiceEndpoint;
import com.netflix.spinnaker.kork.client.ServiceClientProvider;
import java.util.List;
import okhttp3.Interceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(Front50ConfigurationProperties.class)
public class RetrofitConfig {

  @Bean
  @ConditionalOnProperty(name = "services.front50.enabled", matchIfMissing = true)
  Front50Service front50Service(
      Front50ConfigurationProperties front50ConfigurationProperties,
      Interceptor spinnakerRequestHeaderInterceptor,
      ServiceClientProvider serviceClientProvider) {
    return serviceClientProvider.getService(
        Front50Service.class,
        new DefaultServiceEndpoint("front50", front50ConfigurationProperties.getBaseUrl()),
        new ObjectMapper(),
        List.of(spinnakerRequestHeaderInterceptor));
  }
}
