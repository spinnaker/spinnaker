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
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    converters.add(new MapToStringHttpMessageConverter());
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
    if (trustSettings == null || !trustSettings.isEnabled() || StringUtils.isEmpty(trustSettings.getTrustStore())) {
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

  /**
   * An HttpMessageConverter capable of converting a map to url encoded form values.
   *
   * Will only apply if the content type of the request has been explicitly set to application/x-www-form-urlencoded.
   */
  public class MapToStringHttpMessageConverter extends AbstractHttpMessageConverter<Map<String, Object>> {
    MapToStringHttpMessageConverter() {
      super(Charset.defaultCharset(), MediaType.APPLICATION_FORM_URLENCODED);
    }

    @Override
    protected boolean supports(Class<?> clazz) {
      return Map.class.isAssignableFrom(clazz);
    }

    @Override
    protected Map<String, Object> readInternal(Class<? extends Map<String, Object>> clazz,
                                               HttpInputMessage inputMessage) throws HttpMessageNotReadableException {
      throw new UnsupportedOperationException();
    }

    @Override
    protected void writeInternal(Map<String, Object> body,
                                 HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
      Charset charset = getContentTypeCharset(outputMessage.getHeaders().getContentType());

      String str = body.entrySet().stream()
        .map(p -> urlEncode(p.getKey(), charset) + "=" + urlEncode(p.getValue().toString(), charset))
        .reduce((p1, p2) -> p1 + "&" + p2)
        .orElse("");

      StreamUtils.copy(str, charset, outputMessage.getBody());
    }

    private Charset getContentTypeCharset(MediaType contentType) {
      if (contentType != null && contentType.getCharset() != null) {
        return contentType.getCharset();
      }

      return getDefaultCharset();
    }

    private String urlEncode(String str, Charset charset) {
      try {
        return URLEncoder.encode(str, charset.name());
      } catch (UnsupportedEncodingException e) {
        throw new IllegalArgumentException(e);
      }
    }
  }
}
