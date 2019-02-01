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
package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.MemoryCloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.okhttp.AccountProvidingNetworkInterceptor
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties
import com.netflix.spinnaker.okhttp.SpinnakerRequestInterceptor
import com.netflix.spinnaker.security.AuthenticatedRequest
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.springframework.beans.factory.BeanCreationException
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

@Configuration
@ConditionalOnProperty("clouddriver.enabled")
@Import(RetrofitConfiguration::class)
open class ClouddriverConfiguration {

  @Bean
  open fun accountProvidingNetworkInterceptor(applicationContext: ApplicationContext): Interceptor =
    AccountProvidingNetworkInterceptor(applicationContext)

  @Bean
  open fun clouddriverEndpoint(@Value("\${clouddriver.baseUrl}") clouddriverBaseUrl: String): HttpUrl =
    HttpUrl.parse(clouddriverBaseUrl)
      ?: throw BeanCreationException("Invalid URL: $clouddriverBaseUrl")

  @Bean
  open fun clouddriverService(
    clouddriverEndpoint: HttpUrl,
    objectMapper: ObjectMapper,
    retrofitClient: OkHttpClient,
    spinnakerRequestInterceptor: SpinnakerRequestInterceptor,
    okHttpClientConfigurationProperties: OkHttpClientConfigurationProperties)
    : CloudDriverService {

    // Inline version of SpinnakerRequestInterceptor from kork-web
    retrofitClient.interceptors().add(Interceptor { chain ->
      val requestBuilder = chain.request().newBuilder()
      if (okHttpClientConfigurationProperties.propagateSpinnakerHeaders) {
        AuthenticatedRequest
          .getAuthenticationHeaders()
          .forEach { k, v ->
            v.ifPresent {
              requestBuilder.addHeader(k, it)
            }
          }
      }
      chain.proceed(requestBuilder.build())
    })
    return Retrofit.Builder()
      .addConverterFactory(JacksonConverterFactory.create(objectMapper))
      .addCallAdapterFactory(CoroutineCallAdapterFactory())
      .baseUrl(clouddriverEndpoint)
      .client(retrofitClient)
      .build()
      .create(CloudDriverService::class.java)
  }

  @Bean
  @ConditionalOnMissingBean(CloudDriverCache::class)
  open fun cloudDriverCache(cloudDriverService: CloudDriverService) =
    MemoryCloudDriverCache(cloudDriverService)
}
