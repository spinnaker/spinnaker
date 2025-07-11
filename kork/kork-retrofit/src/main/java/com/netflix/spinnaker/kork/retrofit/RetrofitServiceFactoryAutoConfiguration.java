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

import com.netflix.spinnaker.config.okhttp3.OkHttpClientBuilderProvider;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import com.netflix.spinnaker.kork.client.ServiceClientFactory;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import com.netflix.spinnaker.okhttp.SpinnakerRequestInterceptor;
import com.netflix.spinnaker.retrofit.RetrofitConfigurationProperties;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;

@Configuration
@ConditionalOnProperty(value = "retrofit.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({
  RetrofitConfigurationProperties.class,
  OkHttpClientConfigurationProperties.class
})
public class RetrofitServiceFactoryAutoConfiguration {

  /**
   * Creates OkHttpClientProvider bean for use with RetrofitServiceFactory i.e. for retrofit1
   * clients
   */
  @Bean
  public OkHttpClientProvider okHttpClientProvider(List<OkHttpClientBuilderProvider> providers) {
    return new OkHttpClientProvider(providers);
  }

  @Bean
  RestAdapter.LogLevel retrofitLogLevel(
      RetrofitConfigurationProperties retrofitConfigurationProperties) {
    return retrofitConfigurationProperties.getLogLevel();
  }

  @Bean
  @Qualifier("retrofit1")
  public SpinnakerRequestInterceptor spinnakerRequestInterceptor(
      OkHttpClientConfigurationProperties clientProperties) {
    return new SpinnakerRequestInterceptor(clientProperties.getPropagateSpinnakerHeaders());
  }

  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE)
  ServiceClientFactory serviceClientFactory(
      RestAdapter.LogLevel retrofitLogLevel,
      OkHttpClientProvider clientProvider,
      @Qualifier("retrofit1") RequestInterceptor spinnakerRequestInterceptor) {
    return new RetrofitServiceFactory(
        retrofitLogLevel, clientProvider, spinnakerRequestInterceptor);
  }
}
