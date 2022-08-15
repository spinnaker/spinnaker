/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.config;

import static retrofit.Endpoints.newFixedEndpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jakewharton.retrofit.Ok3Client;
import com.netflix.spinnaker.config.DefaultServiceEndpoint;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.web.selector.DefaultServiceSelector;
import com.netflix.spinnaker.kork.web.selector.SelectableService;
import com.netflix.spinnaker.kork.web.selector.ServiceSelector;
import com.netflix.spinnaker.orca.api.operations.OperationsRunner;
import com.netflix.spinnaker.orca.clouddriver.*;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration;
import com.netflix.spinnaker.orca.retrofit.logging.RetrofitSlf4jLog;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.converter.JacksonConverter;

@Configuration
@Import(RetrofitConfiguration.class)
@ComponentScan({
  "com.netflix.spinnaker.orca.clouddriver",
  "com.netflix.spinnaker.orca.kato.pipeline",
  "com.netflix.spinnaker.orca.kato.tasks"
})
@EnableConfigurationProperties({
  CloudDriverConfigurationProperties.class,
  PollerConfigurationProperties.class
})
@Slf4j
public class CloudDriverConfiguration {

  @ConditionalOnMissingBean(ObjectMapper.class)
  @Bean
  ObjectMapper mapper() {
    return OrcaObjectMapper.newInstance();
  }

  @Bean
  ClouddriverRetrofitBuilder clouddriverRetrofitBuilder(
      ObjectMapper objectMapper,
      OkHttpClientProvider clientProvider,
      RestAdapter.LogLevel retrofitLogLevel,
      RequestInterceptor spinnakerRequestInterceptor,
      CloudDriverConfigurationProperties cloudDriverConfigurationProperties) {
    return new ClouddriverRetrofitBuilder(
        objectMapper,
        clientProvider,
        retrofitLogLevel,
        spinnakerRequestInterceptor,
        cloudDriverConfigurationProperties);
  }

  static class ClouddriverRetrofitBuilder {
    ObjectMapper objectMapper;
    OkHttpClientProvider clientProvider;
    RestAdapter.LogLevel retrofitLogLevel;
    RequestInterceptor spinnakerRequestInterceptor;
    CloudDriverConfigurationProperties cloudDriverConfigurationProperties;

    ClouddriverRetrofitBuilder(
        ObjectMapper objectMapper,
        OkHttpClientProvider clientProvider,
        RestAdapter.LogLevel retrofitLogLevel,
        RequestInterceptor spinnakerRequestInterceptor,
        CloudDriverConfigurationProperties cloudDriverConfigurationProperties) {
      this.objectMapper = objectMapper;
      this.clientProvider = clientProvider;
      this.retrofitLogLevel = retrofitLogLevel;
      this.spinnakerRequestInterceptor = spinnakerRequestInterceptor;
      this.cloudDriverConfigurationProperties = cloudDriverConfigurationProperties;
    }

    <T> T buildWriteableService(Class<T> type) {
      return buildService(type, cloudDriverConfigurationProperties.getCloudDriverBaseUrl());
    }

    private <T> T buildService(Class<T> type, String url) {
      return new RestAdapter.Builder()
          .setRequestInterceptor(spinnakerRequestInterceptor)
          .setEndpoint(newFixedEndpoint(url))
          .setClient(
              new Ok3Client(
                  clientProvider.getClient(new DefaultServiceEndpoint("clouddriver", url))))
          .setLogLevel(retrofitLogLevel)
          .setLog(new RetrofitSlf4jLog(type))
          .setConverter(new JacksonConverter(objectMapper))
          .build()
          .create(type);
    }

    private <T> SelectableService buildReadOnlyService(Class<T> type) {
      List<String> urls =
          cloudDriverConfigurationProperties.getCloudDriverReadOnlyBaseUrls().stream()
              .map(CloudDriverConfigurationProperties.BaseUrl::getBaseUrl)
              .collect(Collectors.toList());

      if (urls.isEmpty()
          || urls.stream()
              .allMatch(
                  url -> url.equals(cloudDriverConfigurationProperties.getCloudDriverBaseUrl()))) {
        log.info(
            "readonly URL not configured for clouddriver, using writeable clouddriver {} for {}",
            cloudDriverConfigurationProperties.getCloudDriverBaseUrl(),
            type.getSimpleName());
      }

      List<ServiceSelector> selectors = new ArrayList<>();
      cloudDriverConfigurationProperties
          .getCloudDriverReadOnlyBaseUrls()
          .forEach(
              url -> {
                ServiceSelector selector =
                    new DefaultServiceSelector(
                        buildService(type, url.getBaseUrl()), url.getPriority(), url.getConfig());

                if (url.getConfig() != null && url.getConfig().get("selectorClass") != null) {
                  try {
                    Class<ServiceSelector> selectorClass =
                        (Class<ServiceSelector>)
                            Class.forName(url.getConfig().get("selectorClass").toString());
                    selector =
                        (ServiceSelector)
                            selectorClass.getConstructors()[0].newInstance(
                                selector.getService(), selector.getPriority(), url.getConfig());
                  } catch (Exception e) {
                    log.error(
                        "Failed to create selector for class {}",
                        url.getConfig().get("selectorClass"));

                    throw new RuntimeException(e);
                  }
                }

                selectors.add(selector);
              });

      return new SelectableService(selectors);
    }
  }

  @Bean
  MortService mortDeployService(ClouddriverRetrofitBuilder builder) {
    return new DelegatingMortService(builder.buildReadOnlyService(MortService.class));
  }

  @Bean
  CloudDriverCacheService clouddriverCacheService(ClouddriverRetrofitBuilder builder) {
    return builder.buildWriteableService(CloudDriverCacheService.class);
  }

  @Bean
  CloudDriverCacheStatusService cloudDriverCacheStatusService(ClouddriverRetrofitBuilder builder) {
    return new DelegatingCloudDriverCacheStatusService(
        builder.buildReadOnlyService(CloudDriverCacheStatusService.class));
  }

  @Bean
  OortService oortDeployService(ClouddriverRetrofitBuilder builder) {
    return new DelegatingOortService(builder.buildReadOnlyService(OortService.class));
  }

  @Bean
  KatoRestService katoDeployService(ClouddriverRetrofitBuilder builder) {
    return builder.buildWriteableService(KatoRestService.class);
  }

  @Bean
  CloudDriverTaskStatusService cloudDriverTaskStatusService(ClouddriverRetrofitBuilder builder) {
    return new DelegatingCloudDriverTaskStatusService(
        builder.buildReadOnlyService(CloudDriverTaskStatusService.class));
  }

  @Bean
  KatoService katoService(
      KatoRestService katoRestService,
      CloudDriverTaskStatusService cloudDriverTaskStatusService,
      RetrySupport retrySupport,
      ObjectMapper objectMapper) {
    return new KatoService(
        katoRestService, cloudDriverTaskStatusService, retrySupport, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean(OperationsRunner.class)
  OperationsRunner katoOperationsRunner(KatoService katoService) {
    return new KatoOperationsRunner(katoService);
  }

  @Bean
  FeaturesRestService featuresRestService(ClouddriverRetrofitBuilder builder) {
    return builder.buildWriteableService(FeaturesRestService.class);
  }
}
