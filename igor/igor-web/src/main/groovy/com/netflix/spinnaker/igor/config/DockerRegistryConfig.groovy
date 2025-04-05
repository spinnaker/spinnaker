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

package com.netflix.spinnaker.igor.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.docker.model.DockerRegistryAccounts
import com.netflix.spinnaker.igor.docker.service.ClouddriverService
import com.netflix.spinnaker.igor.util.RetrofitUtils
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory
import groovy.transform.CompileStatic
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

@Configuration
@ConditionalOnProperty(['services.clouddriver.base-url', 'docker-registry.enabled'])
@EnableConfigurationProperties(DockerRegistryProperties)
@CompileStatic
class DockerRegistryConfig {
    @Bean
    DockerRegistryAccounts dockerRegistryAccounts() {
        new DockerRegistryAccounts()
    }

    @Bean
    ClouddriverService dockerRegistryProxyService(
      OkHttp3ClientConfiguration okHttpClientConfig,
      IgorConfigurationProperties igorConfigurationProperties,
      ObjectMapper objectMapper
    ) {
        def address = igorConfigurationProperties.services.clouddriver.baseUrl ?: 'none'
        if (address == 'none') {
            null
        }

        new Retrofit.Builder()
                .baseUrl(RetrofitUtils.getBaseUrl(address))
                .client(okHttpClientConfig.createForRetrofit2().build())
                .addConverterFactory(JacksonConverterFactory.create(objectMapper))
                .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
                .build()
                .create(ClouddriverService)
    }
}

