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
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.clouddriver.MemoryCloudDriverCache
import io.micrometer.core.instrument.MeterRegistry
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.springframework.beans.factory.BeanCreationException
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

@Configuration
@ConditionalOnProperty("clouddriver.enabled")
class ClouddriverConfiguration {

  @Bean
  fun clouddriverEndpoint(@Value("\${clouddriver.base-url}") clouddriverBaseUrl: String): HttpUrl =
    clouddriverBaseUrl.toHttpUrlOrNull()
      ?: throw BeanCreationException("Invalid URL: $clouddriverBaseUrl")

  @Bean
  fun clouddriverService(
    clouddriverEndpoint: HttpUrl,
    objectMapper: ObjectMapper,
    clientProvider: OkHttpClientProvider
  ):
    CloudDriverService =
      Retrofit.Builder()
        .addConverterFactory(JacksonConverterFactory.create(objectMapper))
        .baseUrl(clouddriverEndpoint)
        .client(clientProvider.getClient(DefaultServiceEndpoint("clouddriver", clouddriverEndpoint.toString())))
        .build()
        .create(CloudDriverService::class.java)

  @Bean
  @ConditionalOnMissingBean(CloudDriverCache::class)
  fun cloudDriverCache(
    cloudDriverService: CloudDriverService,
    meterRegistry: MeterRegistry
  ) =
    MemoryCloudDriverCache(cloudDriverService, meterRegistry)

  @Bean
  fun imageService(
    cloudDriverService: CloudDriverService,
    cloudDriverCache: CloudDriverCache
  ) = ImageService(cloudDriverService, cloudDriverCache)
}
