/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.okhttp;

import com.netflix.spectator.api.Registry;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import okhttp3.Request;
import okhttp3.Response;

public class OkHttp3MetricsInterceptor implements okhttp3.Interceptor {
  private final Registry registry;

  public OkHttp3MetricsInterceptor(Registry registry) {
    this.registry = registry;
  }

  @Override
  public Response intercept(Chain chain) throws IOException {
    long start = System.nanoTime();
    boolean wasSuccessful = false;
    int statusCode = -1;

    Request request = chain.request();

    try {
      Response response = chain.proceed(request);
      wasSuccessful = true;
      statusCode = response.code();

      return response;
    } finally {
      recordTimer(
          registry, request.url().url(), System.nanoTime() - start, statusCode, wasSuccessful);
    }
  }

  static void recordTimer(
      Registry registry, URL requestUrl, Long durationNs, int statusCode, boolean wasSuccessful) {
    registry
        .timer(
            registry
                .createId("okhttp.requests")
                .withTag("requestHost", requestUrl.getHost())
                .withTag("statusCode", String.valueOf(statusCode))
                .withTag("status", bucket(statusCode))
                .withTag("success", wasSuccessful))
        .record(durationNs, TimeUnit.NANOSECONDS);
  }

  private static String bucket(int statusCode) {
    if (statusCode < 0) {
      return "Unknown";
    }

    return Integer.toString(statusCode).charAt(0) + "xx";
  }
}
