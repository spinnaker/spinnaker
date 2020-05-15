/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.keel.retrofit

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties
import okhttp3.logging.HttpLoggingInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Lazy
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

@Configuration
@Import(OkHttp3ClientConfiguration::class)
@EnableConfigurationProperties(OkHttpClientConfigurationProperties::class, KeelRetrofitProperties::class)
class KeelRetrofitConfiguration {

  @Bean
  fun retrofitLoggingInterceptor(@Value("\${retrofit2.log-level:BASIC}") retrofitLogLevel: String) =
    HttpLoggingInterceptor().apply {
      level = HttpLoggingInterceptor.Level.valueOf(retrofitLogLevel)
    }

  /**
   * This bean gets highest precedence so that metrics interceptors can rely on the data supplied by this interceptor.
   *
   * Also, we wire up [FiatPermissionEvaluator] lazily to allow spring to wire up the OkHttpClient
   * fully and avoid circular dependency problem.
   */
  @Bean
  @ConditionalOnMissingBean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  fun spinnakerHeadersInterceptor(@Lazy fiatPermissionEvaluator: FiatPermissionEvaluator) =
    SpinnakerHeadersInterceptor(fiatPermissionEvaluator)

  @Bean
  @ConditionalOnMissingBean
  fun userAgentInterceptor(keelRetrofitProperties: KeelRetrofitProperties) =
    UserAgentInterceptor(keelRetrofitProperties)
}
