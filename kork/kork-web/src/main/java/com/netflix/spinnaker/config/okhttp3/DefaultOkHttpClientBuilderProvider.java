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
 *
 */

package com.netflix.spinnaker.config.okhttp3;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;

import com.netflix.spinnaker.config.ServiceEndpoint;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import okhttp3.CipherSuite;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.TlsVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(Ordered.LOWEST_PRECEDENCE - 1)
@Component
public class DefaultOkHttpClientBuilderProvider implements OkHttpClientBuilderProvider {

  private static final Logger log =
      LoggerFactory.getLogger(DefaultOkHttpClientBuilderProvider.class);

  private final OkHttpClient okHttpClient;
  private final OkHttpClientConfigurationProperties okHttpClientConfigurationProperties;

  @Autowired
  public DefaultOkHttpClientBuilderProvider(
      OkHttpClient okHttpClient,
      OkHttpClientConfigurationProperties okHttpClientConfigurationProperties) {
    this.okHttpClient = okHttpClient;
    this.okHttpClientConfigurationProperties = okHttpClientConfigurationProperties;
  }

  @Override
  public OkHttpClient.Builder get(ServiceEndpoint service) {
    OkHttpClient.Builder builder = okHttpClient.newBuilder();
    setSSLSocketFactory(builder, service);
    applyConnectionSpecs(builder);
    return builder;
  }

  protected OkHttpClient.Builder setSSLSocketFactory(
      OkHttpClient.Builder builder, ServiceEndpoint serviceEndpoint) {

    if ((okHttpClientConfigurationProperties.getKeyStore() == null
            && okHttpClientConfigurationProperties.getTrustStore() == null)
        || serviceEndpoint.isUseDefaultSslSocketFactory()) {
      return builder;
    }

    try {
      KeyManagerFactory keyManagerFactory =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      KeyStore ks = KeyStore.getInstance(okHttpClientConfigurationProperties.getKeyStoreType());
      ks.load(
          new FileInputStream(okHttpClientConfigurationProperties.getKeyStore()),
          okHttpClientConfigurationProperties.getKeyStorePassword().toCharArray());
      keyManagerFactory.init(
          ks, okHttpClientConfigurationProperties.getKeyStorePassword().toCharArray());

      TrustManagerFactory trustManagerFactory =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      KeyStore ts = KeyStore.getInstance(okHttpClientConfigurationProperties.getTrustStoreType());
      ts.load(
          new FileInputStream(okHttpClientConfigurationProperties.getTrustStore()),
          okHttpClientConfigurationProperties.getTrustStorePassword().toCharArray());
      trustManagerFactory.init(ts);

      SecureRandom secureRandom =
          SecureRandom.getInstance(
              okHttpClientConfigurationProperties.getSecureRandomInstanceType());
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(
          keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), secureRandom);
      TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
      checkState(
          trustManagers.length == 1, "Found multiple trust managers; don't know which one to use");
      checkState(
          trustManagers[0] instanceof X509TrustManager,
          "Configured TrustManager is a %s, not an X509TrustManager; don't know how to configure it",
          trustManagers[0].getClass().getSimpleName());
      builder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustManagers[0]);
    } catch (Exception e) {
      log.error("Unable to set ssl socket factory for {}", serviceEndpoint.getBaseUrl(), e);
      throw new SystemException(
          format("Unable to set ssl socket factory for (%s)", serviceEndpoint.getBaseUrl()), e);
    }

    return builder;
  }

  protected OkHttpClient.Builder applyConnectionSpecs(OkHttpClient.Builder builder) {

    ConnectionSpec.Builder connectionSpecBuilder =
        new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS);
    if (okHttpClientConfigurationProperties.getCipherSuites() != null) {
      connectionSpecBuilder.cipherSuites(
          okHttpClientConfigurationProperties.getCipherSuites().toArray(new String[0]));
    } else {
      connectionSpecBuilder.cipherSuites(
          Objects.requireNonNull(ConnectionSpec.MODERN_TLS.cipherSuites()).stream()
              .map(CipherSuite::javaName)
              .toArray(String[]::new));
    }

    if (okHttpClientConfigurationProperties.getTlsVersions() != null) {
      connectionSpecBuilder.tlsVersions(
          okHttpClientConfigurationProperties.getTlsVersions().toArray(new String[0]));
    } else {
      connectionSpecBuilder.tlsVersions(
          Objects.requireNonNull(ConnectionSpec.MODERN_TLS.tlsVersions()).stream()
              .map(TlsVersion::javaName)
              .toArray(String[]::new));
    }

    ConnectionSpec connectionSpec = connectionSpecBuilder.build();

    return builder.connectionSpecs(Arrays.asList(connectionSpec, ConnectionSpec.CLEARTEXT));
  }
}
