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

package com.netflix.spinnaker.echo.rest;

import com.squareup.okhttp.OkHttpClient;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OkHttpClientFactoryImpl implements OkHttpClientFactory {

  private static final Logger log = LoggerFactory.getLogger(OkHttpClientFactoryImpl.class);

  public OkHttpClient getInsecureClient() {
    OkHttpClient okHttpClient = new OkHttpClient();

    try {
      // Create a trust manager that does not validate certificate chains
      final TrustManager[] trustAllCerts =
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
      sslContext.init(null, trustAllCerts, null);

      okHttpClient.setSslSocketFactory(sslContext.getSocketFactory());
      okHttpClient.setHostnameVerifier((hostname, session) -> true);
    } catch (Exception e) {
      log.error("Error creating insecure trust manager", e);
    }

    return okHttpClient;
  }
}
