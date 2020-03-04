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

import static com.google.common.base.Preconditions.checkState
import com.netflix.spinnaker.okhttp.OkHttp3MetricsInterceptor
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties
import com.netflix.spinnaker.okhttp.OkHttpMetricsInterceptor
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

@Slf4j
@CompileStatic
@Component
class OkHttp3ClientConfiguration {
  private final OkHttpClientConfigurationProperties okHttpClientConfigurationProperties
  private final OkHttp3MetricsInterceptor okHttp3MetricsInterceptor

  @Autowired
  public OkHttp3ClientConfiguration(OkHttpClientConfigurationProperties okHttpClientConfigurationProperties,
                                    OkHttp3MetricsInterceptor okHttp3MetricsInterceptor) {
    this.okHttpClientConfigurationProperties = okHttpClientConfigurationProperties
    this.okHttp3MetricsInterceptor = okHttp3MetricsInterceptor
  }

  public OkHttp3ClientConfiguration(OkHttpClientConfigurationProperties okHttpClientConfigurationProperties) {
    this.okHttpClientConfigurationProperties = okHttpClientConfigurationProperties
  }

  /**
   * @return OkHttpClient w/ <optional> key and trust stores
   */
  OkHttpClient.Builder create() {
    OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
      .connectTimeout(okHttpClientConfigurationProperties.connectTimeoutMs, TimeUnit.MILLISECONDS)
      .readTimeout(okHttpClientConfigurationProperties.readTimeoutMs, TimeUnit.MILLISECONDS)
      .retryOnConnectionFailure(okHttpClientConfigurationProperties.retryOnConnectionFailure)
      .connectionPool(new ConnectionPool(
        okHttpClientConfigurationProperties.connectionPool.maxIdleConnections,
        okHttpClientConfigurationProperties.connectionPool.keepAliveDurationMs,
        TimeUnit.MILLISECONDS))

    if (okHttp3MetricsInterceptor != null) {
      okHttpClientBuilder.addInterceptor(okHttp3MetricsInterceptor)
    }

    if (!okHttpClientConfigurationProperties.keyStore && !okHttpClientConfigurationProperties.trustStore) {
      return okHttpClientBuilder
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
    def trustManagers = trustManagerFactory.getTrustManagers()
    checkState(trustManagers.length == 1, "Found multiple trust managers; don't know which one to use")
    checkState(trustManagers.first() instanceof X509TrustManager, "Configured TrustManager is a %s, not an X509TrustManager; don't know how to configure it", trustManagers.first().class.getName())
    okHttpClientBuilder.sslSocketFactory(sslContext.socketFactory, (X509TrustManager) trustManagers.first())

    return applyConnectionSpecs(okHttpClientBuilder)
  }

  @CompileDynamic
  private OkHttpClient.Builder applyConnectionSpecs(OkHttpClient.Builder okHttpClientBuilder) {
    def cipherSuites = (okHttpClientConfigurationProperties.cipherSuites ?: ConnectionSpec.MODERN_TLS.cipherSuites()*.javaName) as String[]
    def tlsVersions = (okHttpClientConfigurationProperties.tlsVersions ?: ConnectionSpec.MODERN_TLS.tlsVersions()*.javaName) as String[]

    def connectionSpec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .cipherSuites(cipherSuites)
      .tlsVersions(tlsVersions)
      .build()

    return okHttpClientBuilder.connectionSpecs([connectionSpec, ConnectionSpec.CLEARTEXT] as List<ConnectionSpec>)
  }
}
