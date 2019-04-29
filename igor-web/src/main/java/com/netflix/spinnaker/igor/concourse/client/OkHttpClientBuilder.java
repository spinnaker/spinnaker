/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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

package com.netflix.spinnaker.igor.concourse.client;

import com.netflix.spinnaker.igor.concourse.client.model.Token;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Response;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import okio.Buffer;
import okio.BufferedSource;

public class OkHttpClientBuilder {
  private static TrustManager[] trustAllCerts =
      new TrustManager[] {
        new X509TrustManager() {
          @Override
          public void checkClientTrusted(X509Certificate[] x509Certificates, String s) {}

          @Override
          public void checkServerTrusted(X509Certificate[] x509Certificates, String s) {}

          @Override
          public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
          }
        }
      };

  public static OkHttpClient retryingClient(Supplier<Token> refreshToken) {
    OkHttpClient okHttpClient = new OkHttpClient();
    okHttpClient
        .interceptors()
        .add(chain -> OkHttpClientBuilder.createRetryInterceptor(chain, refreshToken));
    okHttpClient.setHostnameVerifier((s, sslSession) -> true);
    okHttpClient.setSslSocketFactory(getSslContext().getSocketFactory());
    okHttpClient.setConnectTimeout(15, TimeUnit.SECONDS);
    okHttpClient.setReadTimeout(15, TimeUnit.SECONDS);
    return okHttpClient;
  }

  private static SSLContext getSslContext() {
    SSLContext sslContext;
    try {
      sslContext = SSLContext.getInstance("SSL");
      sslContext.init(null, trustAllCerts, new SecureRandom());
    } catch (KeyManagementException | NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    return sslContext;
  }

  public static okhttp3.OkHttpClient retryingClient3(Supplier<Token> refreshToken) {
    return new okhttp3.OkHttpClient.Builder()
        .addInterceptor(chain -> OkHttpClientBuilder.createRetryInterceptor3(chain, refreshToken))
        .hostnameVerifier((s, sslSession) -> true)
        .sslSocketFactory(getSslContext().getSocketFactory(), (X509TrustManager) trustAllCerts[0])
        .connectTimeout(Duration.ofSeconds(15))
        .readTimeout(Duration.ofSeconds(15))
        .build();
  }

  private static Response createRetryInterceptor(
      Interceptor.Chain chain, Supplier<Token> refreshToken) {
    Retry retry =
        Retry.of(
            "concourse.api.call",
            RetryConfig.custom().retryExceptions(RetryableApiException.class).build());

    AtomicReference<Response> lastResponse = new AtomicReference<>();
    try {
      return retry.executeCallable(
          () -> {
            Response response = chain.proceed(chain.request());
            lastResponse.set(response);

            switch (response.code()) {
              case 401:
                String body = null;
                if (response.body() != null) {
                  BufferedSource source = response.body().source();
                  source.request(Long.MAX_VALUE); // request the entire body
                  Buffer buffer = source.buffer();
                  body = buffer.clone().readString(Charset.forName("UTF-8"));
                }
                if (body == null || !body.contains("Bad credentials")) {
                  response =
                      chain.proceed(
                          chain
                              .request()
                              .newBuilder()
                              .header(
                                  "Authorization", "bearer " + refreshToken.get().getAccessToken())
                              .build());
                  lastResponse.set(response);
                }
                break;
              case 502:
              case 503:
              case 504:
                // after retries fail, the response body for these status codes will get wrapped up
                // into a ConcourseApiException
                throw new RetryableApiException();
            }

            return response;
          });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (Exception e) {
      return lastResponse.get();
    }
  }

  private static okhttp3.Response createRetryInterceptor3(
      okhttp3.Interceptor.Chain chain, Supplier<Token> refreshToken) {
    Retry retry =
        Retry.of(
            "concourse.api.call",
            RetryConfig.custom().retryExceptions(RetryableApiException.class).build());

    AtomicReference<okhttp3.Response> lastResponse = new AtomicReference<>();
    try {
      return retry.executeCallable(
          () -> {
            okhttp3.Response response = chain.proceed(chain.request());
            lastResponse.set(response);

            switch (response.code()) {
              case 401:
                String body = null;
                if (response.body() != null) {
                  BufferedSource source = response.body().source();
                  source.request(Long.MAX_VALUE); // request the entire body
                  Buffer buffer = source.buffer();
                  body = buffer.clone().readString(Charset.forName("UTF-8"));
                }
                if (body == null || !body.contains("Bad credentials")) {
                  response =
                      chain.proceed(
                          chain
                              .request()
                              .newBuilder()
                              .header(
                                  "Authorization", "bearer " + refreshToken.get().getAccessToken())
                              .build());
                  lastResponse.set(response);
                }
                break;
              case 502:
              case 503:
              case 504:
                // after retries fail, the response body for these status codes will get wrapped up
                // into a ConcourseApiException
                throw new RetryableApiException();
            }

            return response;
          });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (Exception e) {
      return lastResponse.get();
    }
  }

  private static class RetryableApiException extends RuntimeException {}
}
