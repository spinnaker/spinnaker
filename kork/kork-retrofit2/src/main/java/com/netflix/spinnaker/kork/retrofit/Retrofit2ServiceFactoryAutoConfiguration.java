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

package com.netflix.spinnaker.kork.retrofit;

import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import com.netflix.spinnaker.kork.client.ServiceClientFactory;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration
@ConditionalOnProperty(value = "retrofit2.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(Retrofit2ConfigurationProperties.class)
public class Retrofit2ServiceFactoryAutoConfiguration {

  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE - 1)
  ServiceClientFactory serviceClientFactory2(OkHttpClientProvider clientProvider) {
    return new Retrofit2ServiceFactory(clientProvider);
  }

  @Bean
  @ConditionalOnMissingBean
  HttpLoggingInterceptor httpLoggingInterceptor(
      Retrofit2ConfigurationProperties retrofit2ConfigurationProperties) {
    HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor();
    httpLoggingInterceptor.setLevel(retrofit2ConfigurationProperties.getLogLevel());
    return httpLoggingInterceptor;
  }
}
