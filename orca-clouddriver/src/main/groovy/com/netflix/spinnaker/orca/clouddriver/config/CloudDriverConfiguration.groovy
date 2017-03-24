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

package com.netflix.spinnaker.orca.clouddriver.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.*
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import com.netflix.spinnaker.orca.retrofit.logging.RetrofitSlf4jLog
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.client.Client
import retrofit.converter.JacksonConverter
import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@Import(RetrofitConfiguration)
@ComponentScan([
  "com.netflix.spinnaker.orca.clouddriver",
  "com.netflix.spinnaker.orca.oort.pipeline",
  "com.netflix.spinnaker.orca.oort.tasks",
  "com.netflix.spinnaker.orca.kato.pipeline",
  "com.netflix.spinnaker.orca.kato.tasks"
])
@CompileStatic
@EnableConfigurationProperties(CloudDriverConfigurationProperties)
@Slf4j
class CloudDriverConfiguration {


  @ConditionalOnMissingBean(ObjectMapper)
  @Bean
  ObjectMapper mapper() {
    OrcaObjectMapper.newInstance()
  }

  @Bean
  ClouddriverRetrofitBuilder clouddriverRetrofitBuilder(ObjectMapper objectMapper,
                                                        Client retrofitClient,
                                                        RestAdapter.LogLevel retrofitLogLevel,
                                                        RequestInterceptor spinnakerRequestInterceptor,
                                                        CloudDriverConfigurationProperties cloudDriverConfigurationProperties) {
    return new ClouddriverRetrofitBuilder(objectMapper, retrofitClient, retrofitLogLevel, spinnakerRequestInterceptor, cloudDriverConfigurationProperties)
  }


  static class ClouddriverRetrofitBuilder {
    ObjectMapper objectMapper
    Client retrofitClient
    RestAdapter.LogLevel retrofitLogLevel
    RequestInterceptor spinnakerRequestInterceptor
    CloudDriverConfigurationProperties cloudDriverConfigurationProperties

    ClouddriverRetrofitBuilder(ObjectMapper objectMapper,
                               Client retrofitClient,
                               RestAdapter.LogLevel retrofitLogLevel,
                               RequestInterceptor spinnakerRequestInterceptor,
                               CloudDriverConfigurationProperties cloudDriverConfigurationProperties) {
      this.objectMapper = objectMapper
      this.retrofitClient = retrofitClient
      this.retrofitLogLevel = retrofitLogLevel
      this.spinnakerRequestInterceptor = spinnakerRequestInterceptor
      this.cloudDriverConfigurationProperties = cloudDriverConfigurationProperties
    }

    public <T> T buildWriteableService(Class<T> type) {
      return buildService(type, cloudDriverConfigurationProperties.cloudDriverBaseUrl)
    }

    private <T> T buildService(Class<T> type, String url) {
      new RestAdapter.Builder()
          .setRequestInterceptor(spinnakerRequestInterceptor)
          .setEndpoint(newFixedEndpoint(url))
          .setClient(retrofitClient)
          .setLogLevel(retrofitLogLevel)
          .setLog(new RetrofitSlf4jLog(type))
          .setConverter(new JacksonConverter(objectMapper))
          .build()
          .create(type)
    }

    public <T> T buildReadOnlyService(Class<T> type) {
      if (cloudDriverConfigurationProperties.cloudDriverReadOnlyBaseUrl == cloudDriverConfigurationProperties.cloudDriverBaseUrl) {
        log.info("readonly URL not configured for clouddriver, using writeable clouddriver $cloudDriverConfigurationProperties.cloudDriverBaseUrl for $type.simpleName")
      }

      return buildService(type, cloudDriverConfigurationProperties.cloudDriverReadOnlyBaseUrl)
    }
  }

  @Bean
  MortService mortDeployService(ClouddriverRetrofitBuilder builder) {
    return builder.buildReadOnlyService(MortService)
  }

  @Bean
  CloudDriverCacheService clouddriverCacheService(ClouddriverRetrofitBuilder builder) {
    return builder.buildWriteableService(CloudDriverCacheService)
  }

  @Bean
  CloudDriverCacheStatusService cloudDriverCacheStatusService(ClouddriverRetrofitBuilder builder) {
    return builder.buildReadOnlyService(CloudDriverCacheStatusService)
  }

  @Bean
  OortService oortDeployService(ClouddriverRetrofitBuilder builder) {
    return builder.buildReadOnlyService(OortService)
  }

  @Bean
  KatoRestService katoDeployService(ClouddriverRetrofitBuilder builder) {
    return builder.buildWriteableService(KatoRestService)
  }

  @Bean
  FeaturesRestService featuresRestService(ClouddriverRetrofitBuilder builder) {
    return builder.buildWriteableService(FeaturesRestService)
  }
}
