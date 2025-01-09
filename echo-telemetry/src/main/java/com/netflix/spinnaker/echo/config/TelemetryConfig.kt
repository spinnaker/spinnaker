/*
 * Copyright 2019 Armory, Inc.
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
package com.netflix.spinnaker.echo.config

import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration
import com.netflix.spinnaker.config.okhttp3.DefaultOkHttpClientBuilderProvider
import com.netflix.spinnaker.echo.config.TelemetryConfig.TelemetryConfigProps
import com.netflix.spinnaker.echo.telemetry.TelemetryService
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties
import de.huxhorn.sulky.ulid.ULID
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

@Configuration
@ConditionalOnProperty(value = ["stats.enabled"], matchIfMissing = true)
@EnableConfigurationProperties(TelemetryConfigProps::class)
open class TelemetryConfig {

  companion object {
    private val log = LoggerFactory.getLogger(TelemetryConfig::class.java)
  }

  @Bean
  open fun telemetryService(
    configProps: TelemetryConfigProps,
    okHttpClientConfig: OkHttp3ClientConfiguration,
    okHttpClient: OkHttpClient
  ): TelemetryService {
    val clientProps = OkHttpClientConfigurationProperties(configProps.connectionTimeoutMillis.toLong(), configProps.readTimeoutMillis.toLong())
    val clientProvider = DefaultOkHttpClientBuilderProvider(okHttpClient, clientProps)
    log.info("Telemetry service loaded")
    return Retrofit.Builder()
      .baseUrl(configProps.endpoint)
      .client(clientProvider.get(DefaultServiceEndpoint("telemetry", configProps.endpoint)).build())
      .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
      .addConverterFactory(JacksonConverterFactory.create())
      .build()
      .create(TelemetryService::class.java)
  }

  @ConfigurationProperties(prefix = "stats")
  class TelemetryConfigProps {

    companion object {
      const val DEFAULT_TELEMETRY_ENDPOINT = "https://stats.spinnaker.io"
    }

    var enabled = false
    var endpoint = DEFAULT_TELEMETRY_ENDPOINT
    var instanceId = ULID().nextULID()
    var spinnakerVersion = "unknown"
    var deploymentMethod = DeploymentMethod()
    var connectionTimeoutMillis = 3000
    var readTimeoutMillis = 5000

    class DeploymentMethod {
      var type: String? = null
      var version: String? = null
    }
  }
}
