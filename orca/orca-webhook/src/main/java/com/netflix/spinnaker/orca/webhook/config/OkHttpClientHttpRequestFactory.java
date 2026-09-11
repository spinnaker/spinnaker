/*
 * Copyright 2002-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.webhook.config;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.Assert;

/**
 * Vendored from Spring Framework 6.2 {@code OkHttp3ClientHttpRequestFactory} (removed in Spring 7).
 * Temporary bridge to preserve orca-webhook's OkHttp-based {@code RestTemplate} behavior (custom
 * SSL, interceptors, timeouts) after Spring dropped OkHttp support. Revisit if webhooks migrate to
 * JDK/Jetty/Reactor clients.
 */
public class OkHttpClientHttpRequestFactory implements ClientHttpRequestFactory, DisposableBean {

  private OkHttpClient client;

  private final boolean defaultClient;

  /** Create a factory with a default {@link OkHttpClient} instance. */
  public OkHttpClientHttpRequestFactory() {
    this.client = new OkHttpClient();
    this.defaultClient = true;
  }

  /** Create a factory with the given {@link OkHttpClient} instance. */
  public OkHttpClientHttpRequestFactory(OkHttpClient client) {
    Assert.notNull(client, "OkHttpClient must not be null");
    this.client = client;
    this.defaultClient = false;
  }

  /** Set the underlying read timeout in milliseconds. */
  public void setReadTimeout(int readTimeout) {
    this.client = this.client.newBuilder().readTimeout(readTimeout, TimeUnit.MILLISECONDS).build();
  }

  /** Set the underlying read timeout. */
  public void setReadTimeout(Duration readTimeout) {
    this.client = this.client.newBuilder().readTimeout(readTimeout).build();
  }

  /** Set the underlying write timeout in milliseconds. */
  public void setWriteTimeout(int writeTimeout) {
    this.client =
        this.client.newBuilder().writeTimeout(writeTimeout, TimeUnit.MILLISECONDS).build();
  }

  /** Set the underlying write timeout. */
  public void setWriteTimeout(Duration writeTimeout) {
    this.client = this.client.newBuilder().writeTimeout(writeTimeout).build();
  }

  /** Set the underlying connect timeout in milliseconds. */
  public void setConnectTimeout(int connectTimeout) {
    this.client =
        this.client.newBuilder().connectTimeout(connectTimeout, TimeUnit.MILLISECONDS).build();
  }

  /** Set the underlying connect timeout. */
  public void setConnectTimeout(Duration connectTimeout) {
    this.client = this.client.newBuilder().connectTimeout(connectTimeout).build();
  }

  @Override
  public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
    return new OkHttpClientHttpRequest(this.client, uri, httpMethod);
  }

  @Override
  public void destroy() throws IOException {
    if (this.defaultClient) {
      Cache cache = this.client.cache();
      if (cache != null) {
        cache.close();
      }
      this.client.dispatcher().executorService().shutdown();
      this.client.connectionPool().evictAll();
    }
  }
}
