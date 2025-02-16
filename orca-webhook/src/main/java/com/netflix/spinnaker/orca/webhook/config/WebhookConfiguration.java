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

import com.netflix.spinnaker.kork.crypto.TrustStores;
import com.netflix.spinnaker.kork.crypto.X509Identity;
import com.netflix.spinnaker.kork.crypto.X509IdentitySource;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import com.netflix.spinnaker.orca.config.UserConfiguredUrlRestrictions;
import com.netflix.spinnaker.orca.webhook.util.UnionX509TrustManager;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.*;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

@Configuration
@ConditionalOnProperty(prefix = "webhook.stage", value = "enabled", matchIfMissing = true)
@ComponentScan("com.netflix.spinnaker.orca.webhook")
@EnableConfigurationProperties(WebhookProperties.class)
public class WebhookConfiguration {
  private final WebhookProperties webhookProperties;

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Autowired
  public WebhookConfiguration(WebhookProperties webhookProperties) {
    this.webhookProperties = webhookProperties;
  }

  @Bean
  @ConditionalOnMissingBean(RestTemplate.class)
  public RestTemplate restTemplate(ClientHttpRequestFactory webhookRequestFactory) {
    var restTemplate = new RestTemplate(webhookRequestFactory);

    var converters = restTemplate.getMessageConverters();
    converters.add(new ObjectStringHttpMessageConverter());
    converters.add(new MapToStringHttpMessageConverter());
    restTemplate.setMessageConverters(converters);

    return restTemplate;
  }

  @Bean
  public ClientHttpRequestFactory webhookRequestFactory(
      OkHttpClientConfigurationProperties okHttpClientConfigurationProperties,
      UserConfiguredUrlRestrictions userConfiguredUrlRestrictions,
      WebhookProperties webhookProperties)
      throws IOException {
    var trustManager = webhookX509TrustManager();
    var sslSocketFactory = getSSLSocketFactory(trustManager);
    var builder =
        new OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustManager)
            .addNetworkInterceptor(
                chain -> {
                  Request request = chain.request();
                  validateRequestSize(request, webhookProperties.getMaxRequestBytes());

                  Response response = chain.proceed(request);

                  validateResponseSize(response, webhookProperties.getMaxResponseBytes());

                  if (webhookProperties.isVerifyRedirects() && response.isRedirect()) {
                    // verify that we are not redirecting to a restricted url
                    String redirectLocation = response.header("Location");
                    if (redirectLocation != null && !redirectLocation.trim().startsWith("/")) {
                      userConfiguredUrlRestrictions.validateURI(redirectLocation);
                    }
                  }

                  return response;
                });

    if (webhookProperties.isInsecureSkipHostnameVerification()) {
      builder.hostnameVerifier((hostname, session) -> true);
    }

    var client = builder.build();
    var requestFactory = new OkHttp3ClientHttpRequestFactory(client);
    requestFactory.setReadTimeout(
        Math.toIntExact(okHttpClientConfigurationProperties.getReadTimeoutMs()));
    requestFactory.setConnectTimeout(
        Math.toIntExact(okHttpClientConfigurationProperties.getConnectTimeoutMs()));
    return requestFactory;
  }

  /** Return if the request size is valid. Throw an exception otherwise. */
  private void validateRequestSize(Request request, long maxRequestBytes) throws IOException {
    if (maxRequestBytes <= 0L) {
      return;
    }

    long headerSize = request.headers().byteCount();

    // If the headers are already too big, no need to deal with the body size
    if (headerSize > maxRequestBytes) {
      String message =
          String.format(
              "rejecting request to %s with %s byte(s) of headers, maxRequestBytes: %s",
              request.url(), headerSize, maxRequestBytes);
      log.info(message);
      throw new IllegalArgumentException(message);
    }

    RequestBody requestBody = request.body();
    if (requestBody == null) {
      return;
    }

    long contentLength = 0L;
    try {
      contentLength = requestBody.contentLength();
      if (contentLength == -1) {
        // Given that OkHttp3ClientHttpRequestFactory.buildRequest (called by
        // OkHttp3ClientRequest.executeInternal) has a byte array argument, this
        // doesn't seem possible.  Reject the request in case it ends up being
        // too big.  Yes, this is paranoid, but seems safer.
        String message =
            String.format(
                "unable to verify size of body in request to %s, content length not set",
                request.url());
        log.error(message);
        throw new IllegalStateException(message);
      }
    } catch (IOException e) {
      log.error("exception retrieving content length of request to {}", request.url(), e);
      throw e;
    }

    long totalSize = headerSize + contentLength;
    if (totalSize > maxRequestBytes) {
      String message =
          String.format(
              "rejecting request to %s with %s byte body, maxRequestBytes: {}",
              request.url(), totalSize, maxRequestBytes);
      log.info(message);
      throw new IllegalArgumentException(message);
    }
  }

  /** Return if the response size is valid. Throw an exception otherwise. */
  private void validateResponseSize(Response response, long maxResponseBytes) throws IOException {
    if (maxResponseBytes <= 0L) {
      return;
    }

    long headerSize = response.headers().byteCount();

    // If the headers are already too big, no need to deal with the body size
    if (headerSize > maxResponseBytes) {
      String message =
          String.format(
              "rejecting response from %s with %s byte(s) of headers, maxResponseBytes: %s",
              response.request().url(), headerSize, maxResponseBytes);
      log.info(message);
      throw new IllegalArgumentException(message);
    }

    ResponseBody responseBody = response.body();
    if (responseBody == null) {
      return;
    }

    long contentLength = responseBody.contentLength();
    if (contentLength == -1) {
      // Nothing has read the body yet.  This is the likely case.  Peek into the
      // body to see if it's too long.  See okhttp.Response.peekBody for
      // inspiration.  Note that e.g.
      //
      // contentLength = responseBody.bytes().length
      //
      // consumes the response such that future processing fails.  The underlying buffer is closed.
      BufferedSource peeked = responseBody.source().peek();

      long remainingAllowedBytes = maxResponseBytes - headerSize; // >= 0.  Yes, this could be 0.
      try {
        // Request one more than the number of allowed bytes remaining.  If that
        // succeeds, the response is too long.
        if (peeked.request(remainingAllowedBytes + 1)) {
          String message =
              String.format(
                  "rejecting response from %s. headers: %s byte(s), body > %s byte(s), maxResponseBytes: %s",
                  response.request().url(), headerSize, remainingAllowedBytes, maxResponseBytes);
          log.info(message);
          throw new IllegalArgumentException(message);
        }

        // There aren't enough bytes in the response to satisfy the peek
        // request.  In other words, the response is short enough to be valid.
        return;
      } catch (IOException e) {
        log.error("exception peeking into response from {}", response.request().url(), e);
        throw e;
      }
    }

    long totalSize = headerSize + contentLength;
    if (totalSize > maxResponseBytes) {
      String message =
          String.format(
              "rejecting %s byte response from %s, maxResponseBytes: %s",
              totalSize, response.request().url(), maxResponseBytes);
      log.info(message);
      throw new IllegalArgumentException(message);
    }
  }

  private X509TrustManager webhookX509TrustManager() {
    var trustManagers = new ArrayList<X509TrustManager>();

    trustManagers.add(getTrustManager(null));
    getCustomTrustStore().ifPresent(keyStore -> trustManagers.add(getTrustManager(keyStore)));

    if (webhookProperties.isInsecureTrustSelfSigned()) {
      trustManagers.add(
          new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}

            @Override
            public X509Certificate[] getAcceptedIssuers() {
              return new X509Certificate[0];
            }
          });
    }

    return new UnionX509TrustManager(trustManagers);
  }

  private SSLSocketFactory getSSLSocketFactory(X509TrustManager trustManager) throws IOException {
    try {
      var identityOpt = getCustomIdentity();
      if (identityOpt.isPresent()) {
        var identity = identityOpt.get();
        return identity.createSSLContext(trustManager).getSocketFactory();
      } else {
        var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new X509TrustManager[] {trustManager}, null);
        return sslContext.getSocketFactory();
      }
    } catch (KeyManagementException | NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  private X509TrustManager getTrustManager(KeyStore keyStore) {
    try {
      var trustManagerFactory =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init(keyStore);
      var trustManagers = trustManagerFactory.getTrustManagers();
      return (X509TrustManager) trustManagers[0];
    } catch (KeyStoreException | NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  private Optional<X509Identity> getCustomIdentity() throws IOException {
    var identitySettings = webhookProperties.getIdentity();
    if (identitySettings == null || !identitySettings.isEnabled()) {
      return Optional.empty();
    }

    if (StringUtils.isNotEmpty(identitySettings.getIdentityStore())) {
      var identity =
          X509IdentitySource.fromKeyStore(
              Path.of(identitySettings.getIdentityStore()),
              identitySettings.getIdentityStoreType(),
              () -> {
                var password = identitySettings.getIdentityStorePassword();
                return password == null ? new char[0] : password.toCharArray();
              },
              () -> {
                var password = identitySettings.getIdentityStoreKeyPassword();
                return password == null ? new char[0] : password.toCharArray();
              });
      return Optional.of(identity.load());
    } else if (StringUtils.isNotEmpty(identitySettings.getIdentityKeyPem())) {
      return Optional.of(
          X509IdentitySource.fromPEM(
                  Path.of(identitySettings.getIdentityKeyPem()),
                  Path.of(identitySettings.getIdentityCertPem()))
              .load());
    }

    return Optional.empty();
  }

  private Optional<KeyStore> getCustomTrustStore() {
    var trustSettings = webhookProperties.getTrust();
    if (trustSettings == null || !trustSettings.isEnabled()) {
      return Optional.empty();
    }

    // Use keystore first if set, then try PEM
    if (StringUtils.isNotEmpty(trustSettings.getTrustStore())) {
      KeyStore keyStore;
      try {
        keyStore = KeyStore.getInstance(trustSettings.getTrustStoreType());
      } catch (KeyStoreException e) {
        throw new RuntimeException(e);
      }

      try (FileInputStream file = new FileInputStream(trustSettings.getTrustStore())) {
        keyStore.load(file, trustSettings.getTrustStorePassword().toCharArray());
      } catch (CertificateException | IOException | NoSuchAlgorithmException e) {
        throw new RuntimeException(e);
      }

      return Optional.of(keyStore);
    } else if (StringUtils.isNotEmpty(trustSettings.getTrustPem())) {
      try {
        return Optional.of(TrustStores.loadPEM(Path.of(trustSettings.getTrustPem())));
      } catch (CertificateException
          | IOException
          | NoSuchAlgorithmException
          | KeyStoreException e) {
        throw new RuntimeException(e);
      }
    }

    return Optional.empty();
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
   * <p>Will only apply if the content type of the request has been explicitly set to
   * application/x-www-form-urlencoded.
   */
  public class MapToStringHttpMessageConverter
      extends AbstractHttpMessageConverter<Map<String, Object>> {
    MapToStringHttpMessageConverter() {
      super(Charset.defaultCharset(), MediaType.APPLICATION_FORM_URLENCODED);
    }

    @Override
    protected boolean supports(Class<?> clazz) {
      return Map.class.isAssignableFrom(clazz);
    }

    @Override
    protected Map<String, Object> readInternal(
        Class<? extends Map<String, Object>> clazz, HttpInputMessage inputMessage)
        throws HttpMessageNotReadableException {
      throw new UnsupportedOperationException();
    }

    @Override
    protected void writeInternal(Map<String, Object> body, HttpOutputMessage outputMessage)
        throws IOException, HttpMessageNotWritableException {
      Charset charset = getContentTypeCharset(outputMessage.getHeaders().getContentType());

      String str =
          body.entrySet().stream()
              .map(
                  p ->
                      urlEncode(p.getKey(), charset)
                          + "="
                          + urlEncode(p.getValue().toString(), charset))
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
