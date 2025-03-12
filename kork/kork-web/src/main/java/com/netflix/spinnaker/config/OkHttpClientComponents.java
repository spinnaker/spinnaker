/*
 * Copyright 2016 Netflix, Inc.; Copyright 2023 Apple Inc.
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

package com.netflix.spinnaker.config;

import brave.http.HttpTracing;
import brave.okhttp3.TracingInterceptor;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientCustomizer;
import com.netflix.spinnaker.kork.crypto.PasswordProvider;
import com.netflix.spinnaker.kork.crypto.SecureRandomBuilder;
import com.netflix.spinnaker.kork.crypto.StandardCrypto;
import com.netflix.spinnaker.kork.crypto.TrustStores;
import com.netflix.spinnaker.kork.crypto.X509Identity;
import com.netflix.spinnaker.kork.crypto.X509IdentitySource;
import com.netflix.spinnaker.okhttp.OkHttp3MetricsInterceptor;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import com.netflix.spinnaker.okhttp.SpinnakerRequestHeaderInterceptor;
import com.netflix.spinnaker.okhttp.SpinnakerRequestInterceptor;
import com.netflix.spinnaker.retrofit.Retrofit2ConfigurationProperties;
import com.netflix.spinnaker.retrofit.RetrofitConfigurationProperties;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.inject.Provider;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import lombok.RequiredArgsConstructor;
import okhttp3.ConnectionPool;
import okhttp3.ConnectionSpec;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.task.TaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.task.support.ExecutorServiceAdapter;
import org.springframework.util.CollectionUtils;

/** Provides OkHttpClient beans. */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
@EnableConfigurationProperties({
  OkHttpClientConfigurationProperties.class,
  OkHttpMetricsInterceptorProperties.class,
  RetrofitConfigurationProperties.class,
  Retrofit2ConfigurationProperties.class
})
public class OkHttpClientComponents {
  private final Provider<Registry> registryProvider;
  private final OkHttpClientConfigurationProperties clientProperties;
  private final OkHttpMetricsInterceptorProperties metricsProperties;
  private final Retrofit2ConfigurationProperties retrofit2Properties;

  @Bean
  public SpinnakerRequestInterceptor spinnakerRequestInterceptor() {
    return new SpinnakerRequestInterceptor(clientProperties.getPropagateSpinnakerHeaders());
  }

  @Bean
  public SpinnakerRequestHeaderInterceptor spinnakerRequestHeaderInterceptor() {
    return new SpinnakerRequestHeaderInterceptor(clientProperties.getPropagateSpinnakerHeaders());
  }

  @Bean
  public OkHttp3MetricsInterceptor okHttp3MetricsInterceptor() {
    return new OkHttp3MetricsInterceptor(registryProvider, metricsProperties);
  }

  /** Adds a metrics interceptor to clients. */
  @Bean
  public OkHttpClientCustomizer metricsInterceptorCustomizer(
      OkHttp3MetricsInterceptor metricsInterceptor) {
    return builder -> builder.addInterceptor(metricsInterceptor);
  }

  @Bean
  public OkHttpClientCustomizer requestHeaderInterceptorCustomizer(
      SpinnakerRequestHeaderInterceptor headerInterceptor) {
    return builder -> builder.addInterceptor(headerInterceptor);
  }

  /**
   * Adds an HTTP tracing interceptor to clients if enabled.
   *
   * @see HttpTracing
   */
  @Bean
  @ConditionalOnBean(HttpTracing.class)
  public OkHttpClientCustomizer tracingInterceptorCustomizer(HttpTracing httpTracing) {
    return builder -> builder.addNetworkInterceptor(TracingInterceptor.create(httpTracing));
  }

  /**
   * Configures a common dispatcher for clients.
   *
   * @see org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration
   */
  @Bean
  public Dispatcher okhttpDispatcher(TaskExecutorBuilder taskExecutorBuilder) {
    var dispatcher = new Dispatcher(new ExecutorServiceAdapter(taskExecutorBuilder.build()));
    dispatcher.setMaxRequests(clientProperties.getMaxRequests());
    dispatcher.setMaxRequestsPerHost(clientProperties.getMaxRequestsPerHost());
    return dispatcher;
  }

  /** Configures request dispatching for clients. */
  @Bean
  public OkHttpClientCustomizer dispatcherCustomizer(Dispatcher dispatcher) {
    return builder -> builder.dispatcher(dispatcher);
  }

  /**
   * Configures connection pooling for clients.
   *
   * @see ConnectionPool
   */
  @Bean
  public OkHttpClientCustomizer connectionPoolCustomizer() {
    var poolProperties = clientProperties.getConnectionPool();
    var connectionPool =
        new ConnectionPool(
            poolProperties.getMaxIdleConnections(),
            poolProperties.getKeepAliveDurationMs(),
            TimeUnit.MILLISECONDS);
    return builder -> builder.connectionPool(connectionPool);
  }

  /**
   * Configures connection specifications allowed for clients.
   *
   * @see ConnectionSpec
   */
  @Bean
  public OkHttpClientCustomizer connectionSpecsCustomizer() {
    var connectionSpecBuilder = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS);
    var cipherSuites = clientProperties.getCipherSuites();
    if (!CollectionUtils.isEmpty(cipherSuites)) {
      connectionSpecBuilder.cipherSuites(cipherSuites.toArray(String[]::new));
    }
    var tlsVersions = clientProperties.getTlsVersions();
    if (!CollectionUtils.isEmpty(tlsVersions)) {
      connectionSpecBuilder.tlsVersions(tlsVersions.toArray(String[]::new));
    }
    var tlsConnectionSpec = connectionSpecBuilder.build();
    var connectionSpecs = List.of(tlsConnectionSpec, ConnectionSpec.CLEARTEXT);
    return builder -> builder.connectionSpecs(connectionSpecs);
  }

  /**
   * Configures an {@link SSLContext} for clients.
   *
   * @see X509IdentitySource
   * @see X509TrustManager
   * @see SecureRandom
   * @see javax.net.ssl.SSLSocketFactory
   */
  @Bean
  public OkHttpClientCustomizer sslContextCustomizer()
      throws IOException, GeneralSecurityException {
    var identity = loadKeyStore();
    var trustManager = loadTrustStore();
    var secureRandom = loadSecureRandom();
    SSLContext context;
    if (identity != null) {
      context = identity.createSSLContext(trustManager, secureRandom);
    } else {
      context = StandardCrypto.getTLSContext();
      context.init(null, new TrustManager[] {trustManager}, secureRandom);
    }
    return builder -> builder.sslSocketFactory(context.getSocketFactory(), trustManager);
  }

  @Nullable
  private X509Identity loadKeyStore() {
    File keyStoreFile = clientProperties.getKeyStore();
    if (keyStoreFile == null) {
      return null;
    }
    PasswordProvider passwordProvider = () -> clientProperties.getKeyStorePassword().toCharArray();
    return X509IdentitySource.fromKeyStore(
            keyStoreFile.toPath(), clientProperties.getKeyStoreType(), passwordProvider)
        .refreshable(clientProperties.getRefreshableKeys().getRefreshPeriod());
  }

  private X509TrustManager loadTrustStore() throws IOException, GeneralSecurityException {
    File trustStoreFile = clientProperties.getTrustStore();
    if (trustStoreFile == null) {
      return TrustStores.getSystemTrustManager();
    }
    try (var stream = new FileInputStream(trustStoreFile)) {
      KeyStore truststore = KeyStore.getInstance(clientProperties.getTrustStoreType());
      truststore.load(stream, clientProperties.getTrustStorePassword().toCharArray());
      return TrustStores.loadTrustManager(truststore);
    }
  }

  private SecureRandom loadSecureRandom() {
    try {
      return SecureRandom.getInstance(clientProperties.getSecureRandomInstanceType());
    } catch (NoSuchAlgorithmException ignored) {
    }
    try {
      return SecureRandom.getInstanceStrong();
    } catch (NoSuchAlgorithmException ignored) {
    }
    return SecureRandomBuilder.create().withPersonalizationString("OkHttp3").build();
  }

  /** Configures connection timeout settings for clients. */
  @Bean
  public OkHttpClientCustomizer connectionTimeoutsCustomizer() {
    var connectTimeout = Duration.ofMillis(clientProperties.getConnectTimeoutMs());
    var readTimeout = Duration.ofMillis(clientProperties.getReadTimeoutMs());
    var retryOnConnectionFailure = clientProperties.isRetryOnConnectionFailure();
    return builder ->
        builder
            .connectTimeout(connectTimeout)
            .readTimeout(readTimeout)
            .retryOnConnectionFailure(retryOnConnectionFailure);
  }

  @Bean
  public OkHttpClientCustomizer httpLoggingCustomizer() {
    return builder ->
        builder.addInterceptor(
            new HttpLoggingInterceptor().setLevel(retrofit2Properties.getLogLevel()));
  }

  /**
   * Prototype bean for client builders. These prototypes come preconfigured with all registered
   * client customizers applied.
   *
   * @see OkHttpClientCustomizer
   */
  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public OkHttpClient.Builder okHttpClientBuilder(
      ObjectProvider<OkHttpClientCustomizer> customizers) {
    var builder = new OkHttpClient.Builder();
    customizers.orderedStream().forEachOrdered(customizer -> customizer.customize(builder));
    return builder;
  }
}
