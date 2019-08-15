/*
 * Copyright 2015 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.config

import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties
import com.netflix.spinnaker.okhttp.OkHttpMetricsInterceptor
import com.squareup.okhttp.ConnectionPool
import com.squareup.okhttp.ConnectionSpec
import com.squareup.okhttp.OkHttpClient
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * @deprecated replaced by {@link OkHttp3ClientConfiguration}
 */

@Slf4j
@CompileStatic
@Component
@Deprecated // see OkHttp3ClientConfiguration
class OkHttpClientConfiguration {

  private final OkHttpClientConfigurationProperties okHttpClientConfigurationProperties
  private final OkHttpMetricsInterceptor okHttpMetricsInterceptor

  @Autowired
  public OkHttpClientConfiguration(OkHttpClientConfigurationProperties okHttpClientConfigurationProperties,
                                   OkHttpMetricsInterceptor okHttpMetricsInterceptor) {
    this.okHttpClientConfigurationProperties = okHttpClientConfigurationProperties
    this.okHttpMetricsInterceptor = okHttpMetricsInterceptor
  }

  /**
   * @return OkHttpClient w/ <optional> key and trust stores
   */
  OkHttpClient create() {

    def okHttpClient = new OkHttpClient()
    okHttpClient.setConnectTimeout(okHttpClientConfigurationProperties.connectTimeoutMs, TimeUnit.MILLISECONDS)
    okHttpClient.setReadTimeout(okHttpClientConfigurationProperties.readTimeoutMs, TimeUnit.MILLISECONDS)
    okHttpClient.setRetryOnConnectionFailure(okHttpClientConfigurationProperties.retryOnConnectionFailure)
    okHttpClient.interceptors().add(okHttpMetricsInterceptor)
    okHttpClient.connectionPool = new ConnectionPool(
      okHttpClientConfigurationProperties.connectionPool.maxIdleConnections,
      okHttpClientConfigurationProperties.connectionPool.keepAliveDurationMs)

    if (!okHttpClientConfigurationProperties.keyStore && !okHttpClientConfigurationProperties.trustStore) {
      return okHttpClient
    }

    def sslContext = SSLContext.getInstance('TLS')

    def keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
    def ks = KeyStore.getInstance(okHttpClientConfigurationProperties.keyStoreType)
    okHttpClientConfigurationProperties.keyStore.withInputStream {
      ks.load(it as InputStream, okHttpClientConfigurationProperties.keyStorePassword.toCharArray())
    }
    keyManagerFactory.init(ks, okHttpClientConfigurationProperties.keyStorePassword.toCharArray())

    def trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    def ts = KeyStore.getInstance(okHttpClientConfigurationProperties.trustStoreType)
    okHttpClientConfigurationProperties.trustStore.withInputStream {
      ts.load(it as InputStream, okHttpClientConfigurationProperties.trustStorePassword.toCharArray())
    }
    trustManagerFactory.init(ts)

    def secureRandom = new SecureRandom()
    try {
      secureRandom = SecureRandom.getInstance(okHttpClientConfigurationProperties.secureRandomInstanceType)
    } catch (NoSuchAlgorithmException e) {
      log.error("Unable to fetch secure random instance for ${okHttpClientConfigurationProperties.secureRandomInstanceType}", e)
    }

    sslContext.init(keyManagerFactory.keyManagers, trustManagerFactory.trustManagers, secureRandom)
    okHttpClient.setSslSocketFactory(sslContext.socketFactory)

    return applyConnectionSpecs(okHttpClient)
  }

  @CompileDynamic
  private OkHttpClient applyConnectionSpecs(OkHttpClient okHttpClient) {
    def cipherSuites = (okHttpClientConfigurationProperties.cipherSuites ?: ConnectionSpec.MODERN_TLS.cipherSuites()*.javaName) as String[]
    def tlsVersions = (okHttpClientConfigurationProperties.tlsVersions ?: ConnectionSpec.MODERN_TLS.tlsVersions()*.javaName) as String[]

    def connectionSpec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .cipherSuites(cipherSuites)
      .tlsVersions(tlsVersions)
      .build()

    return okHttpClient.setConnectionSpecs([connectionSpec, ConnectionSpec.CLEARTEXT] as List<ConnectionSpec>)
  }
}
