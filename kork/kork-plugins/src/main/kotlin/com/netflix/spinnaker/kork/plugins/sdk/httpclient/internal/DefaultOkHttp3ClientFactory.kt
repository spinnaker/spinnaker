/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.kork.plugins.sdk.httpclient.internal

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration
import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.kork.plugins.api.httpclient.HttpClientConfig
import com.netflix.spinnaker.kork.plugins.api.httpclient.HttpClientConfig.LoggingConfig.LoggingLevel
import com.netflix.spinnaker.kork.plugins.sdk.httpclient.OkHttp3ClientFactory
import com.netflix.spinnaker.okhttp.OkHttp3MetricsInterceptor
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties
import com.netflix.spinnaker.okhttp.Retrofit2EncodeCorrectionInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Configures a standard [OkHttpClient].
 */
class DefaultOkHttp3ClientFactory(
  private val okHttpClientHttp3MetricsInterceptor: OkHttp3MetricsInterceptor,
  private val retrofit2LogLevel : HttpLoggingInterceptor.Level
) : OkHttp3ClientFactory {
  override fun supports(baseUrl: String): Boolean =
    baseUrl.startsWith("http://") || baseUrl.startsWith("https://")

  override fun create(baseUrl: String, config: HttpClientConfig): OkHttpClient {
    // TODO(rz): Add plugin ID to the metrics. Requires refactoring existing metrics interceptor.
    return OkHttp3ClientConfiguration(convertToOkHttp(config), okHttpClientHttp3MetricsInterceptor, retrofit2LogLevel)
      .createForRetrofit2()
      .also {
        if (config.logging.level != LoggingLevel.NONE) {
          it.addInterceptor(
            HttpLoggingInterceptor().apply {
              level = when (config.logging.level) {
                LoggingLevel.BASIC -> HttpLoggingInterceptor.Level.BASIC
                LoggingLevel.HEADERS -> HttpLoggingInterceptor.Level.HEADERS
                LoggingLevel.BODY -> HttpLoggingInterceptor.Level.BODY
                else -> throw SystemException("Unsupported logging level: ${config.logging.level}")
              }
            }
          )
        }
      }
      .build()
  }

  private fun convertToOkHttp(config: HttpClientConfig): OkHttpClientConfigurationProperties =
    OkHttpClientConfigurationProperties().apply {
      config.connectionPool.keepAlive?.let {
        connectionPool.keepAliveDurationMs = it.toMillis().toInt()
      }
      config.connectionPool.maxIdleConnections?.let {
        connectionPool.maxIdleConnections = it
      }

      config.connection.connectTimeout?.let {
        connectTimeoutMs = it.toMillis()
      }
      config.connection.readTimeout?.let {
        readTimeoutMs = it.toMillis()
      }
      refreshableKeys.enabled = false

      retryOnConnectionFailure = config.connection.isRetryOnConnectionFailure

      if (config.security.keyStorePath != null && config.security.trustStorePath != null) {
        keyStore = config.security.keyStorePath.toFile()
        keyStoreType = config.security.keyStoreType
        keyStorePassword = config.security.keyStorePassword

        trustStore = config.security.trustStorePath.toFile()
        trustStoreType = config.security.trustStoreType
        trustStorePassword = config.security.trustStorePassword

        tlsVersions = config.security.tlsVersions
        cipherSuites = config.security.cipherSuites
      }
    }

}
