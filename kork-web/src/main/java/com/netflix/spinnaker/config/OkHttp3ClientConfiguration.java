/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.config;

import com.netflix.spinnaker.okhttp.OkHttp3MetricsInterceptor;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import okhttp3.CipherSuite;
import okhttp3.ConnectionPool;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.TlsVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OkHttp3ClientConfiguration {
  private final OkHttpClientConfigurationProperties properties;
  private final OkHttp3MetricsInterceptor okHttp3MetricsInterceptor;

  @Autowired
  public OkHttp3ClientConfiguration(
      OkHttpClientConfigurationProperties okHttpClientConfigurationProperties,
      OkHttp3MetricsInterceptor okHttp3MetricsInterceptor) {
    this.properties = okHttpClientConfigurationProperties;
    this.okHttp3MetricsInterceptor = okHttp3MetricsInterceptor;
  }

  /** @return OkHttpClient w/ <optional> key and trust stores */
  public OkHttpClient.Builder create()
      throws NoSuchAlgorithmException, KeyStoreException, IOException, CertificateException,
          UnrecoverableKeyException, KeyManagementException {
    OkHttpClient.Builder okHttpClientBuilder =
        new OkHttpClient.Builder()
            .connectTimeout(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
            .readTimeout(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(properties.isRetryOnConnectionFailure())
            .connectionPool(
                new ConnectionPool(
                    properties.getConnectionPool().getMaxIdleConnections(),
                    properties.getConnectionPool().getKeepAliveDurationMs(),
                    TimeUnit.MILLISECONDS))
            .addInterceptor(okHttp3MetricsInterceptor);

    if (properties.getKeyStore() == null && properties.getTrustStore() == null) {
      return okHttpClientBuilder;
    }

    SSLContext sslContext = SSLContext.getInstance("TLS");

    KeyManagerFactory keyManagerFactory =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    initKeyStore(keyManagerFactory);

    TrustManagerFactory trustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    initTrustStore(trustManagerFactory);

    sslContext.init(
        keyManagerFactory.getKeyManagers(),
        trustManagerFactory.getTrustManagers(),
        getSecureRandom());
    okHttpClientBuilder.sslSocketFactory(sslContext.getSocketFactory());

    return applyConnectionSpecs(okHttpClientBuilder);
  }

  private void initKeyStore(KeyManagerFactory keyManagerFactory)
      throws CertificateException, NoSuchAlgorithmException, IOException, KeyStoreException,
          UnrecoverableKeyException {
    final KeyStore ks = KeyStore.getInstance(properties.getKeyStoreType());

    try (InputStream is = new FileInputStream(properties.getKeyStore())) {
      ks.load(is, properties.getKeyStorePassword().toCharArray());
    }
    keyManagerFactory.init(ks, properties.getKeyStorePassword().toCharArray());
  }

  private void initTrustStore(TrustManagerFactory trustManagerFactory)
      throws CertificateException, NoSuchAlgorithmException, IOException, KeyStoreException {
    final KeyStore ts = KeyStore.getInstance(properties.getTrustStoreType());

    try (InputStream is = new FileInputStream(properties.getTrustStore())) {
      ts.load(is, properties.getTrustStorePassword().toCharArray());
    }
    trustManagerFactory.init(ts);
  }

  private OkHttpClient.Builder applyConnectionSpecs(OkHttpClient.Builder okHttpClientBuilder) {
    ConnectionSpec connectionSpec =
        new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .cipherSuites(getCipherSuites())
            .tlsVersions(getTlsVersions())
            .build();
    return okHttpClientBuilder.connectionSpecs(
        Arrays.asList(connectionSpec, ConnectionSpec.CLEARTEXT));
  }

  private String[] getCipherSuites() {
    final List<String> suites = properties.getCipherSuites();
    if (suites == null || suites.isEmpty()) {
      List<CipherSuite> cipherSuites =
          Objects.requireNonNull(ConnectionSpec.MODERN_TLS.cipherSuites());
      return cipherSuites.stream()
          .map(CipherSuite::javaName)
          .collect(Collectors.toList())
          .toArray(new String[cipherSuites.size()]);
    }
    return suites.toArray(new String[0]);
  }

  private String[] getTlsVersions() {
    final List<String> versions = properties.getTlsVersions();
    if (versions == null || versions.isEmpty()) {
      List<TlsVersion> tlsVersions =
          Objects.requireNonNull(ConnectionSpec.MODERN_TLS.tlsVersions());
      return tlsVersions.stream()
          .map(TlsVersion::javaName)
          .collect(Collectors.toList())
          .toArray(new String[tlsVersions.size()]);
    }
    return versions.toArray(new String[0]);
  }

  private SecureRandom getSecureRandom() {
    try {
      return SecureRandom.getInstance(properties.getSecureRandomInstanceType());
    } catch (NoSuchAlgorithmException e) {
      log.error(
          "Unable to fetch secure random instance for " + properties.getSecureRandomInstanceType(),
          e);
    }
    return new SecureRandom();
  }
}
