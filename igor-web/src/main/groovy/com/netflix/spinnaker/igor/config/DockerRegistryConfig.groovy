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

import com.jakewharton.retrofit.Ok3Client
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.docker.model.DockerRegistryAccounts
import com.netflix.spinnaker.igor.docker.service.ClouddriverService
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger
import groovy.transform.CompileStatic
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.Endpoints
import retrofit.RestAdapter

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
    ClouddriverService dockerRegistryProxyService(OkHttpClientProvider clientProvider,
                                                  IgorConfigurationProperties igorConfigurationProperties,
                                                  RestAdapter.LogLevel retrofitLogLevel) {
        def address = igorConfigurationProperties.services.clouddriver.baseUrl ?: 'none'
        if (address == 'none') {
            null
        }

        new RestAdapter.Builder()
                .setEndpoint(Endpoints.newFixedEndpoint(address))
                .setClient(new Ok3Client(clientProvider.getClient(new DefaultServiceEndpoint("clouddriver", address))))
                .setLogLevel(retrofitLogLevel)
                .setLog(new Slf4jRetrofitLogger(ClouddriverService))
                .build()
                .create(ClouddriverService)
    }
}

