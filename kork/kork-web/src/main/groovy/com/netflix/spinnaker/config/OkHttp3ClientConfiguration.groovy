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

import com.netflix.spinnaker.okhttp.Retrofit2EncodeCorrectionInterceptor
import com.netflix.spinnaker.okhttp.SpinnakerRequestHeaderInterceptor
import okhttp3.Dispatcher
import okhttp3.logging.HttpLoggingInterceptor
import org.springframework.beans.factory.ObjectFactory

import static com.google.common.base.Preconditions.checkState
import com.netflix.spinnaker.okhttp.OkHttp3MetricsInterceptor
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties
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
  private final ObjectFactory<OkHttpClient.Builder> httpClientBuilderFactory

  /**
   * Logging level for retrofit2 client calls
  */
  private final HttpLoggingInterceptor.Level retrofit2LogLevel

  /**
   *  {@link okhttp3.Interceptor} which adds spinnaker auth headers to requests when retrofit2 client used
   */
  private final SpinnakerRequestHeaderInterceptor spinnakerRequestHeaderInterceptor

  /**
   * {@link okhttp3.Interceptor} for correcting partial encoding done by Retrofit2.  Do not use in retrofit1.
   */
  private final Retrofit2EncodeCorrectionInterceptor retrofit2EncodeCorrectionInterceptor

  @Autowired
  OkHttp3ClientConfiguration(OkHttpClientConfigurationProperties okHttpClientConfigurationProperties,
                             OkHttp3MetricsInterceptor okHttp3MetricsInterceptor,
                             HttpLoggingInterceptor.Level retrofit2LogLevel,
                             SpinnakerRequestHeaderInterceptor spinnakerRequestHeaderInterceptor,
                             Retrofit2EncodeCorrectionInterceptor retrofit2EncodeCorrectionInterceptor,
                             ObjectFactory<OkHttpClient.Builder> httpClientBuilderFactory) {
    this.okHttpClientConfigurationProperties = okHttpClientConfigurationProperties
    this.okHttp3MetricsInterceptor = okHttp3MetricsInterceptor
    this.retrofit2LogLevel = retrofit2LogLevel
    this.spinnakerRequestHeaderInterceptor = spinnakerRequestHeaderInterceptor
    this.retrofit2EncodeCorrectionInterceptor = retrofit2EncodeCorrectionInterceptor
    this.httpClientBuilderFactory = httpClientBuilderFactory
  }

  public OkHttp3ClientConfiguration(OkHttpClientConfigurationProperties okHttpClientConfigurationProperties,
                                    OkHttp3MetricsInterceptor okHttp3MetricsInterceptor) {
    this(okHttpClientConfigurationProperties, okHttp3MetricsInterceptor, null, null, null,
      { new OkHttpClient.Builder() })
  }

  public OkHttp3ClientConfiguration(OkHttpClientConfigurationProperties okHttpClientConfigurationProperties) {
    this(okHttpClientConfigurationProperties, null)
  }

  /**
   * @return OkHttpClient w/ <optional> key and trust stores.  For use with retrofit1.  Do not use with retrofit2.
   */
  OkHttpClient.Builder create() {
    if (okHttpClientConfigurationProperties.refreshableKeys.enabled) {
      // already configured via OkHttpClientCustomizer beans
      return httpClientBuilderFactory.object
    }

    OkHttpClient.Builder okHttpClientBuilder = createBasicClient()

    if (okHttp3MetricsInterceptor != null) {
      okHttpClientBuilder.addInterceptor(okHttp3MetricsInterceptor)
    }

    if (!okHttpClientConfigurationProperties.keyStore && !okHttpClientConfigurationProperties.trustStore) {
      return okHttpClientBuilder
    }

    return setTruststoreKey(okHttpClientBuilder)
  }

  /**
   * @return OkHttpClient with SpinnakerRequestHeaderInterceptor and Retrofit2EncodeCorrectionInterceptor
   * as initial interceptors w/ <optional> key and trust stores
   */
  OkHttpClient.Builder createForRetrofit2() {
    if (okHttpClientConfigurationProperties.refreshableKeys.enabled) {
      // already configured via OkHttpClientCustomizer beans
      return httpClientBuilderFactory.object
    }

    OkHttpClient.Builder okHttpClientBuilder = createBasicClient()

    /**
     * {@link okhttp3.Interceptor} are sequential, insert spinnakerRequestHeaderInterceptor initially,
     * so next okhttp interceptor aware of these spinnaker auth headers when retrofit2 client used.
     */
    if (spinnakerRequestHeaderInterceptor != null) {
      okHttpClientBuilder.addInterceptor(spinnakerRequestHeaderInterceptor)
    }

    if (okHttp3MetricsInterceptor != null) {
      okHttpClientBuilder.addInterceptor(okHttp3MetricsInterceptor)
    }

    if (retrofit2EncodeCorrectionInterceptor != null) {
      okHttpClientBuilder.addInterceptor(retrofit2EncodeCorrectionInterceptor)
    }

    /**
     * The logging functionality was removed in Retrofit2, since the required HTTP layer is now completely based on OkHttp.
     * Recommend to add logging as the last interceptor, because this will also log the information
     * which you added with previous interceptors to your request.
     */
    okHttpClientBuilder.addInterceptor(new HttpLoggingInterceptor().setLevel(retrofit2LogLevel))

    if (!okHttpClientConfigurationProperties.keyStore && !okHttpClientConfigurationProperties.trustStore) {
      return okHttpClientBuilder
    }

    return setTruststoreKey(okHttpClientBuilder)
  }

  private OkHttpClient.Builder setTruststoreKey(OkHttpClient.Builder okHttpClientBuilder) {
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

  private OkHttpClient.Builder createBasicClient() {
    Dispatcher dispatcher = new Dispatcher()
    dispatcher.setMaxRequests(okHttpClientConfigurationProperties.maxRequests)
    dispatcher.setMaxRequestsPerHost(okHttpClientConfigurationProperties.maxRequestsPerHost)

    OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
      .connectTimeout(okHttpClientConfigurationProperties.connectTimeoutMs, TimeUnit.MILLISECONDS)
      .readTimeout(okHttpClientConfigurationProperties.readTimeoutMs, TimeUnit.MILLISECONDS)
      .retryOnConnectionFailure(okHttpClientConfigurationProperties.retryOnConnectionFailure)
      .dispatcher(dispatcher)
      .connectionPool(new ConnectionPool(
        okHttpClientConfigurationProperties.connectionPool.maxIdleConnections,
        okHttpClientConfigurationProperties.connectionPool.keepAliveDurationMs,
        TimeUnit.MILLISECONDS))
    okHttpClientBuilder
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
