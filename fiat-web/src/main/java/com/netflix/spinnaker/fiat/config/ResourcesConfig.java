/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.fiat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import com.netflix.spinnaker.fiat.providers.ProviderHealthTracker;
import com.netflix.spinnaker.fiat.providers.internal.*;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Scope;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Configuration
@EnableConfigurationProperties(ProviderCacheConfig.class)
@PropertySource("classpath:resilience4j-defaults.properties")
public class ResourcesConfig {
  @Autowired @Setter private ObjectMapper objectMapper;

  @Autowired @Setter private OkHttpClientProvider clientProvider;

  @Value("${services.front50.base-url}")
  @Setter
  private String front50Endpoint;

  @Value("${services.clouddriver.base-url}")
  @Setter
  private String clouddriverEndpoint;

  @Value("${services.igor.base-url}")
  @Setter
  private String igorEndpoint;

  @Bean
  Front50Api front50Api(OkHttp3ClientConfiguration okHttpClientConfig) {
    return new Retrofit.Builder()
        .baseUrl(front50Endpoint)
        .client(okHttpClientConfig.createForRetrofit2().build())
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .addConverterFactory(JacksonConverterFactory.create(objectMapper))
        .build()
        .create(Front50Api.class);
  }

  @Bean
  Front50ApplicationLoader front50ApplicationLoader(
      ProviderHealthTracker tracker, Front50Api front50Api) {
    return new Front50ApplicationLoader(tracker, front50Api);
  }

  @Bean
  Front50ServiceAccountLoader front50ServiceAccountLoader(
      ProviderHealthTracker tracker, Front50Api front50Api) {
    return new Front50ServiceAccountLoader(tracker, front50Api);
  }

  @Bean
  Front50Service front50Service(
      Front50ApplicationLoader front50ApplicationLoader,
      Front50ServiceAccountLoader front50ServiceAccountLoader) {
    return new Front50Service(front50ApplicationLoader, front50ServiceAccountLoader);
  }

  @Bean
  ClouddriverApi clouddriverApi(OkHttp3ClientConfiguration okHttpClientConfig) {
    return new Retrofit.Builder()
        .baseUrl(clouddriverEndpoint)
        .client(okHttpClientConfig.createForRetrofit2().build())
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .addConverterFactory(JacksonConverterFactory.create(objectMapper))
        .build()
        .create(ClouddriverApi.class);
  }

  @Bean
  ClouddriverAccountLoader clouddriverAccountLoader(
      ProviderHealthTracker providerHealthTracker, ClouddriverApi clouddriverApi) {
    return new ClouddriverAccountLoader(providerHealthTracker, clouddriverApi);
  }

  @Bean
  @ConditionalOnProperty(
      name = "resource.provider.application.clouddriver.load-applications",
      havingValue = "true",
      matchIfMissing = true)
  ClouddriverApplicationLoader clouddriverApplicationLoader(
      ProviderHealthTracker providerHealthTracker,
      ClouddriverApi clouddriverApi,
      ResourceProviderConfig resourceProviderConfig) {
    return new ClouddriverApplicationLoader(
        providerHealthTracker, clouddriverApi, resourceProviderConfig.getApplication());
  }

  @Bean
  @ConditionalOnProperty(
      name = "resource.provider.application.clouddriver.load-applications",
      havingValue = "true",
      matchIfMissing = true)
  @Primary
  ClouddriverService clouddriverService(
      ClouddriverApplicationLoader clouddriverApplicationLoader,
      ClouddriverAccountLoader clouddriverAccountLoader) {
    return new ClouddriverService(clouddriverApplicationLoader, clouddriverAccountLoader);
  }

  @Bean
  @ConditionalOnProperty(
      name = "resource.provider.application.clouddriver.load-applications",
      havingValue = "false")
  ClouddriverService clouddriverServiceWithoutApplicationLoader(
      ClouddriverAccountLoader clouddriverAccountLoader) {
    return new ClouddriverService(clouddriverAccountLoader);
  }

  @Bean
  @ConditionalOnProperty("services.igor.enabled")
  IgorApi igorApi(
      @Value("${services.igor.base-url}") String igorEndpoint,
      OkHttp3ClientConfiguration okHttpClientConfig) {
    return new Retrofit.Builder()
        .baseUrl(igorEndpoint)
        .client(okHttpClientConfig.createForRetrofit2().build())
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .addConverterFactory(JacksonConverterFactory.create(objectMapper))
        .build()
        .create(IgorApi.class);
  }

  @Bean
  @ConditionalOnProperty("services.igor.enabled")
  IgorBuildServiceLoader igorBuildServiceLoader(
      ProviderHealthTracker providerHealthTracker, IgorApi igorApi) {
    return new IgorBuildServiceLoader(providerHealthTracker, igorApi);
  }

  @Bean
  @ConditionalOnProperty("services.igor.enabled")
  IgorService igorService(IgorBuildServiceLoader igorBuildServiceLoader) {
    return new IgorService(igorBuildServiceLoader);
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  ProviderHealthTracker providerHealthTracker(ProviderCacheConfig config) {
    return new ProviderHealthTracker(config.getMaximumStalenessTimeMs());
  }
}
