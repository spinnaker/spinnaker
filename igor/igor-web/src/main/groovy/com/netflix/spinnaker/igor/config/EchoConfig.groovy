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

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.kork.retrofit.util.CustomConverterFactory
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit2.Retrofit

/**
 * history service configuration
 */
@ConditionalOnProperty('services.echo.base-url')
@Configuration
class EchoConfig {
    @Bean
    EchoService echoService(
      OkHttp3ClientConfiguration okHttpClientConfig,
      IgorConfigurationProperties igorConfigurationProperties
    ) {
        String address = igorConfigurationProperties.services.echo.baseUrl ?: 'none'

        if (address == 'none') {
            return null
        }

        new Retrofit.Builder()
            .baseUrl(RetrofitUtils.getBaseUrl(address))
            .client(okHttpClientConfig.createForRetrofit2().build())
            .addConverterFactory(CustomConverterFactory.create())
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .build()
            .create(EchoService)
    }
}
