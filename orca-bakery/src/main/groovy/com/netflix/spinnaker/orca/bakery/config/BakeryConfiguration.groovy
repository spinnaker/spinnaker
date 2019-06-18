/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.bakery.config

import com.netflix.spinnaker.orca.bakery.BakerySelector
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.EnableConfigurationProperties
import retrofit.RequestInterceptor

import java.text.SimpleDateFormat
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import com.netflix.spinnaker.orca.retrofit.logging.RetrofitSlf4jLog
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit.RestAdapter
import retrofit.RestAdapter.LogLevel
import retrofit.client.Client
import retrofit.converter.JacksonConverter
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@Import([OrcaConfiguration, RetrofitConfiguration])
@ComponentScan([
  "com.netflix.spinnaker.orca.bakery.pipeline",
  "com.netflix.spinnaker.orca.bakery.tasks"
])
@CompileStatic
@ConditionalOnExpression('${bakery.enabled:true}')
@EnableConfigurationProperties(BakeryConfigurationProperties)
class BakeryConfiguration {

  @Autowired Client retrofitClient
  @Autowired LogLevel retrofitLogLevel
  @Autowired RequestInterceptor spinnakerRequestInterceptor

  @Bean
  BakeryService bakery(@Value('${bakery.base-url}') String bakeryBaseUrl) {
    return buildService(bakeryBaseUrl)
  }

  static ObjectMapper bakeryConfiguredObjectMapper() {
    def objectMapper = new ObjectMapper()
      .setPropertyNamingStrategy(new SnakeCaseStrategy())
      .setDateFormat(new SimpleDateFormat("YYYYMMDDHHmm"))
      .setSerializationInclusion(NON_NULL)
      .disable(FAIL_ON_UNKNOWN_PROPERTIES)

  }

  BakeryService buildService(String url) {
    return new RestAdapter.Builder()
      .setEndpoint(newFixedEndpoint(url))
      .setRequestInterceptor(spinnakerRequestInterceptor)
      .setConverter(new JacksonConverter(bakeryConfiguredObjectMapper()))
      .setClient(retrofitClient)
      .setLogLevel(retrofitLogLevel)
      .setLog(new RetrofitSlf4jLog(BakeryService))
      .build()
      .create(BakeryService)
  }

  @Bean
  BakerySelector bakerySelector(BakeryService bakery,
                                BakeryConfigurationProperties bakeryConfigurationProperties) {
    return new BakerySelector(
      bakery,
      bakeryConfigurationProperties,
      { url -> buildService(url as String) }
    )
  }
}
