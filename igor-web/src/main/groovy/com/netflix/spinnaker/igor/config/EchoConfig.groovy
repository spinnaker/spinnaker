/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.config

import com.netflix.spinnaker.config.OkHttpClientConfiguration
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.Endpoints
import retrofit.RestAdapter
import retrofit.client.OkClient

/**
 * history service configuration
 */
@ConditionalOnProperty('services.echo.base-url')
@Configuration
class EchoConfig {
    @Bean
    EchoService echoService(OkHttpClientConfiguration okHttpClientConfig, IgorConfigurationProperties igorConfigurationProperties) {
        String address = igorConfigurationProperties.services.echo.baseUrl ?: 'none'

        if (address == 'none') {
            return null
        }

        def cli = okHttpClientConfig.create()

        new RestAdapter.Builder()
            .setEndpoint(Endpoints.newFixedEndpoint(address))
            .setClient(new OkClient(cli))
            .setLogLevel(RestAdapter.LogLevel.NONE)
            .setLog(new Slf4jRetrofitLogger(EchoService))
            .build()
            .create(EchoService)
    }
}
