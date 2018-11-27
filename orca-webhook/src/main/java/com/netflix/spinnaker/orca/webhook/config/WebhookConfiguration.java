/*
 * Copyright 2017 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.webhook.config;

import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import com.netflix.spinnaker.orca.webhook.util.UnionX509TrustManager;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Configuration
@ConditionalOnProperty(prefix = "webhook.stage", value = "enabled", matchIfMissing = true)
@ComponentScan("com.netflix.spinnaker.orca.webhook")
@EnableConfigurationProperties(WebhookProperties.class)
public class WebhookConfiguration {
  private final WebhookProperties webhookProperties;

  @Autowired
  public WebhookConfiguration(WebhookProperties webhookProperties) {
    this.webhookProperties = webhookProperties;
  }

  @Bean
  @ConditionalOnMissingBean(RestTemplate.class)
  public RestTemplate restTemplate(ClientHttpRequestFactory webhookRequestFactory) {
    RestTemplate restTemplate = new RestTemplate(webhookRequestFactory);

    List<HttpMessageConverter<?>> converters = restTemplate.getMessageConverters();
    converters.add(new ObjectStringHttpMessageConverter());
    restTemplate.setMessageConverters(converters);

    return restTemplate;
  }

  @Bean
  public ClientHttpRequestFactory webhookRequestFactory(
    OkHttpClientConfigurationProperties okHttpClientConfigurationProperties
  ) {
    X509TrustManager trustManager = webhookX509TrustManager();
    SSLSocketFactory sslSocketFactory = getSSLSocketFactory(trustManager);
    OkHttpClient client = new OkHttpClient.Builder().sslSocketFactory(sslSocketFactory, trustManager).build();
    OkHttp3ClientHttpRequestFactory requestFactory = new OkHttp3ClientHttpRequestFactory(client);
    requestFactory.setReadTimeout(Math.toIntExact(okHttpClientConfigurationProperties.getReadTimeoutMs()));
    requestFactory.setConnectTimeout(Math.toIntExact(okHttpClientConfigurationProperties.getConnectTimeoutMs()));
    return requestFactory;
  }

  private X509TrustManager webhookX509TrustManager() {
    List<X509TrustManager> trustManagers = new ArrayList<>();

    trustManagers.add(getTrustManager(null));
    getCustomKeyStore().ifPresent(keyStore -> trustManagers.add(getTrustManager(keyStore)));

    return new UnionX509TrustManager(trustManagers);
  }

  private SSLSocketFactory getSSLSocketFactory(X509TrustManager trustManager) {
    try {
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, new X509TrustManager[]{ trustManager }, null);
      return sslContext.getSocketFactory();
    } catch (KeyManagementException | NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  private X509TrustManager getTrustManager(KeyStore keyStore) {
    try {
      TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init(keyStore);
      TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
      return (X509TrustManager) trustManagers[0];
    } catch (KeyStoreException | NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  private Optional<KeyStore> getCustomKeyStore() {
    WebhookProperties.TrustSettings trustSettings = webhookProperties.getTrust();
    if (trustSettings == null || StringUtils.isEmpty(trustSettings.getTrustStore())) {
      return Optional.empty();
    }

    KeyStore keyStore;
    try {
      keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    } catch (KeyStoreException e) {
      throw new RuntimeException(e);
    }

    try (FileInputStream file = new FileInputStream(trustSettings.getTrustStore())) {
      keyStore.load(file, trustSettings.getTrustStorePassword().toCharArray());
    } catch (CertificateException | IOException | NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }

    return Optional.of(keyStore);
  }

  public class ObjectStringHttpMessageConverter extends StringHttpMessageConverter {
    @Override
    public boolean supports(Class<?> clazz) {
      return clazz == Object.class;
    }
  }
}
