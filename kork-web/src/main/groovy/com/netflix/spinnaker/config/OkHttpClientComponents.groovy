/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.config

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.okhttp.OkHttp3MetricsInterceptor
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties
import com.netflix.spinnaker.okhttp.OkHttpMetricsInterceptor
import com.netflix.spinnaker.okhttp.SpinnakerRequestHeaderInterceptor
import com.netflix.spinnaker.okhttp.SpinnakerRequestInterceptor
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import javax.inject.Provider

@Configuration
@EnableConfigurationProperties([OkHttpClientConfigurationProperties, OkHttpMetricsInterceptorProperties])
class OkHttpClientComponents {
  @Bean
  SpinnakerRequestInterceptor spinnakerRequestInterceptor(OkHttpClientConfigurationProperties okHttpClientConfigurationProperties) {
    return new SpinnakerRequestInterceptor(okHttpClientConfigurationProperties)
  }

  @Bean
  SpinnakerRequestHeaderInterceptor spinnakerRequestHeaderInterceptor(OkHttpClientConfigurationProperties okHttpClientConfigurationProperties) {
    return new SpinnakerRequestHeaderInterceptor(okHttpClientConfigurationProperties)
  }

  @Bean
  OkHttpMetricsInterceptor okHttpMetricsInterceptor(
    Provider<Registry> registry,
    OkHttpMetricsInterceptorProperties okHttpMetricsInterceptorProperties) {

    return new OkHttpMetricsInterceptor(registry, okHttpMetricsInterceptorProperties)
  }

  @Bean
  OkHttp3MetricsInterceptor okHttp3MetricsInterceptor(
    Provider<Registry> registry,
    OkHttpMetricsInterceptorProperties okHttpMetricsInterceptorProperties) {

    return new OkHttp3MetricsInterceptor(registry, okHttpMetricsInterceptorProperties)
  }
}
