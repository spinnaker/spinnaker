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

import static java.lang.String.format;

import com.netflix.spinnaker.config.ServiceEndpoint;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(Ordered.LOWEST_PRECEDENCE)
@Component
public class InsecureOkHttpClientBuilderProvider implements OkHttpClientBuilderProvider {

  private static final Logger log =
      LoggerFactory.getLogger(InsecureOkHttpClientBuilderProvider.class);

  private final OkHttpClient okHttpClient;

  @Autowired
  public InsecureOkHttpClientBuilderProvider(OkHttpClient okHttpClient) {
    this.okHttpClient = okHttpClient;
  }

  @Override
  public Boolean supports(ServiceEndpoint service) {
    return ((service.getBaseUrl().startsWith("http://")
            || service.getBaseUrl().startsWith("https://"))
        && !service.isSecure());
  }

  @Override
  public OkHttpClient.Builder get(ServiceEndpoint service) {
    OkHttpClient.Builder builder = okHttpClient.newBuilder();
    return setSSLSocketFactory(builder, service);
  }

  private OkHttpClient.Builder setSSLSocketFactory(
      OkHttpClient.Builder builder, ServiceEndpoint service) {

    try {
      // Create a trust manager that does not validate certificate chains
      final TrustManager[] trustManagers =
          new TrustManager[] {
            new X509TrustManager() {
              @Override
              public void checkClientTrusted(
                  java.security.cert.X509Certificate[] chain, String authType) {}

              @Override
              public void checkServerTrusted(
                  java.security.cert.X509Certificate[] chain, String authType) {}

              @Override
              public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return new java.security.cert.X509Certificate[] {};
              }
            }
          };

      final SSLContext sslContext = SSLContext.getInstance("SSL");
      sslContext.init(null, trustManagers, null);
      builder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustManagers[0]);
      builder.hostnameVerifier((hostname, session) -> true);

    } catch (Exception e) {
      log.error("Unable to set ssl socket factory for {}", service.getBaseUrl(), e);
      throw new SystemException(
          format("Unable to set ssl socket factory for (%s)", service.getBaseUrl()), e);
    }

    return builder;
  }
}
