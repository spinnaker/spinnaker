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
 */

package com.netflix.spinnaker.config.okhttp3;

import brave.http.HttpTracing;
import brave.okhttp3.TracingInterceptor;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;

/**
 * Builds an {@link OkHttpClient} that should be can be used as-is or as a foundation for more
 * custom OkHttpClient needs.
 */
public class RawOkHttpClientFactory {

  /**
   * Returns a basic client which can be further customized for other needs in the {@link
   * OkHttpClientProvider} implementations. (eg: SSL setup, name verifier etc)
   */
  public OkHttpClient create(
      OkHttpClientConfigurationProperties okHttpClientConfigurationProperties,
      List<Interceptor> interceptors,
      HttpTracing httpTracing) {

    Dispatcher dispatcher = zipkinHttpTracingDispatcher(httpTracing);

    dispatcher.setMaxRequests(okHttpClientConfigurationProperties.getMaxRequests());
    dispatcher.setMaxRequestsPerHost(okHttpClientConfigurationProperties.getMaxRequestsPerHost());

    OkHttpClient.Builder okHttpClientBuilder =
        new OkHttpClient.Builder()
            .connectTimeout(
                okHttpClientConfigurationProperties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
            .readTimeout(
                okHttpClientConfigurationProperties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(
                okHttpClientConfigurationProperties.getRetryOnConnectionFailure())
            .dispatcher(dispatcher)
            .addNetworkInterceptor(zipkinTracingInterceptor(httpTracing))
            .connectionPool(
                new ConnectionPool(
                    okHttpClientConfigurationProperties.getConnectionPool().getMaxIdleConnections(),
                    okHttpClientConfigurationProperties
                        .getConnectionPool()
                        .getKeepAliveDurationMs(),
                    TimeUnit.MILLISECONDS));

    if (interceptors != null) {
      interceptors.forEach(okHttpClientBuilder::addInterceptor);
    }

    return okHttpClientBuilder.build();
  }

  private static Dispatcher zipkinHttpTracingDispatcher(HttpTracing httpTracing) {
    return new Dispatcher(
        httpTracing
            .tracing()
            .currentTraceContext()
            .executorService(new Dispatcher().executorService()));
  }

  private static Interceptor zipkinTracingInterceptor(HttpTracing httpTracing) {
    return TracingInterceptor.create(httpTracing);
  }
}
