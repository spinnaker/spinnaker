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

import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import com.netflix.spinnaker.okhttp.OkHttpMetricsInterceptor;
import com.squareup.okhttp.CipherSuite;
import com.squareup.okhttp.ConnectionPool;
import com.squareup.okhttp.ConnectionSpec;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.TlsVersion;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
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
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** @deprecated replaced by {@link OkHttp3ClientConfiguration} */
@Slf4j
@Component
@Deprecated
public class OkHttpClientConfiguration {
  @Autowired
  public OkHttpClientConfiguration(
      OkHttpClientConfigurationProperties okHttpClientConfigurationProperties,
      OkHttpMetricsInterceptor okHttpMetricsInterceptor) {
    this.properties = okHttpClientConfigurationProperties;
    this.okHttpMetricsInterceptor = okHttpMetricsInterceptor;
  }

  /** @return OkHttpClient w/ <optional> key and trust stores */
  public OkHttpClient create()
      throws NoSuchAlgorithmException, KeyStoreException, CertificateException,
          UnrecoverableKeyException, IOException, KeyManagementException {
    OkHttpClient okHttpClient = new OkHttpClient();
    okHttpClient.setConnectTimeout(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS);
    okHttpClient.setReadTimeout(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS);
    okHttpClient.setRetryOnConnectionFailure(properties.isRetryOnConnectionFailure());
    okHttpClient.interceptors().add(okHttpMetricsInterceptor);
    okHttpClient.setConnectionPool(
        new ConnectionPool(
            properties.getConnectionPool().getMaxIdleConnections(),
            properties.getConnectionPool().getKeepAliveDurationMs()));

    if (properties.getKeyStore() == null && properties.getTrustStore() == null) {
      return okHttpClient;
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
    okHttpClient.setSslSocketFactory(sslContext.getSocketFactory());

    return applyConnectionSpecs(okHttpClient);
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

  private OkHttpClient applyConnectionSpecs(OkHttpClient okHttpClient) {
    ConnectionSpec connectionSpec =
        new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .cipherSuites(getCipherSuites())
            .tlsVersions(getTlsVersions())
            .build();
    return okHttpClient.setConnectionSpecs(Arrays.asList(connectionSpec, ConnectionSpec.CLEARTEXT));
  }

  private String[] getCipherSuites() {
    final List<String> suites = properties.getCipherSuites();
    if (suites == null || suites.isEmpty()) {
      List<CipherSuite> cipherSuites =
          Objects.requireNonNull(ConnectionSpec.MODERN_TLS.cipherSuites());
      return cipherSuites.stream()
          .map(
              c -> {
                try {
                  Field javaNameField = c.getClass().getField("javaName");
                  javaNameField.setAccessible(true);
                  return (String) javaNameField.get(c);
                } catch (NoSuchFieldException
                    | SecurityException
                    | IllegalArgumentException
                    | IllegalAccessException e) {
                  throw new BeanCreationException("Could not access javaName of CipherSuite", e);
                }
              })
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

  private final OkHttpClientConfigurationProperties properties;
  private final OkHttpMetricsInterceptor okHttpMetricsInterceptor;
}
