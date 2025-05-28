/*
 * Copyright 2025 OpsMx, Inc.
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

package com.netflix.spinnaker.orca.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.config.DefaultServiceClientProvider;
import com.netflix.spinnaker.config.okhttp3.DefaultOkHttpClientBuilderProvider;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientBuilderProvider;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import com.netflix.spinnaker.kork.client.ServiceClientFactory;
import com.netflix.spinnaker.kork.client.ServiceClientProvider;
import com.netflix.spinnaker.kork.retrofit.Retrofit2ServiceFactory;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import java.util.List;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class Retrofit2TestConfig {
  @Bean
  @Primary
  public OkHttpClientConfigurationProperties okHttpClientConfigurationProperties() {
    return new OkHttpClientConfigurationProperties();
  }

  @Bean
  public OkHttpClient okHttpClient() {
    return new OkHttpClient();
  }

  @Bean
  public OkHttpClientBuilderProvider okHttpClientBuilderProvider(
      OkHttpClient okHttpClient,
      OkHttpClientConfigurationProperties okHttpClientConfigurationProperties) {
    return new DefaultOkHttpClientBuilderProvider(
        okHttpClient, okHttpClientConfigurationProperties);
  }

  @Bean
  public OkHttpClientProvider okHttpClientProvider(
      OkHttpClientBuilderProvider okHttpClientBuilderProvider) {
    return new OkHttpClientProvider(List.of(okHttpClientBuilderProvider));
  }

  @Bean
  public ServiceClientFactory serviceClientFactory(OkHttpClientProvider okHttpClientProvider) {
    return new Retrofit2ServiceFactory(okHttpClientProvider);
  }

  @Bean
  public ServiceClientProvider serviceClientProvider(ServiceClientFactory serviceClientFactory) {
    return new DefaultServiceClientProvider(List.of(serviceClientFactory), new ObjectMapper());
  }
}
