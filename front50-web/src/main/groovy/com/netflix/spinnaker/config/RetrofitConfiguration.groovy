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

package com.netflix.spinnaker.config

import com.netflix.spinnaker.config.OkHttpClientConfiguration
import com.squareup.okhttp.ConnectionPool
import com.squareup.okhttp.Interceptor
import com.squareup.okhttp.Response
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Scope
import retrofit.RestAdapter
import retrofit.client.OkClient

@Configuration
@CompileStatic
@Import(OkHttpClientConfiguration)
@EnableConfigurationProperties
class RetrofitConfiguration {

  @Value('${okHttpClient.connectionPool.maxIdleConnections:5}')
  int maxIdleConnections

  @Value('${okHttpClient.connectionPool.keepAliveDurationMs:300000}')
  int keepAliveDurationMs

  @Value('${okHttpClient.retryOnConnectionFailure:true}')
  boolean retryOnConnectionFailure

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  OkClient okClient(OkHttpClientConfiguration okHttpClientConfig) {
    final String userAgent = "Spinnaker-${System.getProperty('spring.application.name', 'unknown')}/${getClass().getPackage().implementationVersion ?: '1.0'}"
    def cfg = okHttpClientConfig.create()
    cfg.networkInterceptors().add(new Interceptor() {
      @Override
      Response intercept(Interceptor.Chain chain) throws IOException {
        def req = chain.request().newBuilder().header('User-Agent', userAgent).build()
        chain.proceed(req)
      }
    })
    cfg.setConnectionPool(new ConnectionPool(maxIdleConnections, keepAliveDurationMs))
    cfg.retryOnConnectionFailure = retryOnConnectionFailure

    new OkClient(cfg)
  }

  @Bean RestAdapter.LogLevel retrofitLogLevel(@Value('${retrofit.logLevel:BASIC}') String retrofitLogLevel) {
    return RestAdapter.LogLevel.valueOf(retrofitLogLevel)
  }
}
