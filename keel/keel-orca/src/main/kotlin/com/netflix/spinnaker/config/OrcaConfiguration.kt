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
import com.netflix.spinnaker.keel.orca.DryRunCapableOrcaService
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.retrofit.InstrumentedJacksonConverter
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import retrofit2.Retrofit
import java.util.concurrent.Executors

@Configuration
@ConditionalOnProperty("orca.enabled")
@ComponentScan("com.netflix.spinnaker.keel.orca")
class OrcaConfiguration {

  @Bean
  fun orcaEndpoint(@Value("\${orca.base-url}") orcaBaseUrl: String) =
    orcaBaseUrl.toHttpUrlOrNull()

  @Bean
  fun orcaService(
    orcaEndpoint: HttpUrl,
    objectMapper: ObjectMapper,
    clientProvider: OkHttpClientProvider,
    springEnv: Environment
  ): OrcaService =
    DryRunCapableOrcaService(
      springEnv = springEnv,
      delegate = Retrofit.Builder()
        .baseUrl(RetrofitUtils.getBaseUrl(orcaEndpoint.toString()))
        .client(clientProvider.getClient(DefaultServiceEndpoint("orca", orcaEndpoint.toString())))
        .addConverterFactory(InstrumentedJacksonConverter.Factory("Orca", objectMapper))
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance(Executors.newCachedThreadPool()))
        .build()
        .create(OrcaService::class.java)
    )
}
