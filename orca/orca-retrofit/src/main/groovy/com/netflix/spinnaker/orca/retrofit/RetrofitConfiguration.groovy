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


package com.netflix.spinnaker.orca.retrofit

import com.jakewharton.retrofit.Ok3Client
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils
import com.netflix.spinnaker.orca.retrofit.exceptions.SpinnakerServerExceptionHandler
import groovy.transform.CompileStatic
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Scope
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import retrofit.RestAdapter.LogLevel

@Configuration
@CompileStatic
@Import(OkHttp3ClientConfiguration)
@EnableConfigurationProperties
class RetrofitConfiguration {
  @Bean(name = ["retrofitClient"])
  @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
  Ok3Client ok3Client(Registry registry,
                      OkHttp3ClientConfiguration okHttpClientConfig,
                      Optional<List<RetrofitInterceptorProvider>> retrofitInterceptorProviders) {
    final String userAgent = "Spinnaker-${System.getProperty('spring.application.name', 'unknown')}/${getClass().getPackage().implementationVersion ?: '1.0'}"
    OkHttpClient.Builder builder = okHttpClientConfig.create()
    builder.addNetworkInterceptor(
      new Interceptor() {
        @Override
        Response intercept(Interceptor.Chain chain) throws IOException {
          def req = chain.request().newBuilder().header('User-Agent', userAgent).build()
          chain.proceed(req)
        }
      })

    (retrofitInterceptorProviders.orElse([])).each { provider ->
      provider.interceptors.each { interceptor ->
        builder.addInterceptor(interceptor);
      }
    }
    OkHttpClient client = RetrofitUtils.getClientForRetrofit1(builder.build())
    new Ok3Client(client)
  }

  @Bean
  LogLevel retrofitLogLevel(@Value('${retrofit.log-level:BASIC}') String retrofitLogLevel) {
    return LogLevel.valueOf(retrofitLogLevel)
  }

  /**
   * Set the order such that this has higher precedence than the
   * DefaultExceptionHandler bean.
   */
  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  SpinnakerServerExceptionHandler spinnakerServerExceptionHandler() {
    new SpinnakerServerExceptionHandler()
  }
}
