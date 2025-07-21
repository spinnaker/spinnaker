/*
 * Copyright 2025 OpsMx, Inc.
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

package com.netflix.spinnaker.kork.retrofit.util;

import com.netflix.spinnaker.okhttp.Retrofit2EncodeCorrectionInterceptor;
import com.netflix.spinnaker.okhttp.SpinnakerRequestHeaderInterceptor;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;

public class RetrofitUtils {

  /**
   * Converts a given URL to a valid base URL for use in a {@link retrofit2.Retrofit} instance. If
   * the URL is invalid, an {@link IllegalArgumentException} is thrown. If the URL does not end with
   * a slash, a slash is appended to the end of the URL.
   *
   * @param suppliedBaseUrl the URL to convert
   * @return a valid base URL for use in a Retrofit instance
   */
  public static String getBaseUrl(String suppliedBaseUrl) {
    HttpUrl parsedUrl = HttpUrl.parse(suppliedBaseUrl);
    if (parsedUrl == null) {
      throw new IllegalArgumentException("Invalid URL: " + suppliedBaseUrl);
    }
    String baseUrl = parsedUrl.newBuilder().build().toString();
    if (!baseUrl.endsWith("/")) {
      baseUrl += "/";
    }
    return baseUrl;
  }

  /**
   * Creates a new client with the retrofit2 specific Interceptors
   * (SpinnakerRequestHeaderInterceptor and Retrofit2EncodeCorrectionInterceptor) removed.
   *
   * @return a new ok client with the correct interceptors
   */
  public static OkHttpClient getClientForRetrofit1(OkHttpClient client) {
    OkHttpClient.Builder clientBuilder = client.newBuilder();
    clientBuilder.interceptors().clear();
    for (Interceptor interceptor : client.interceptors()) {
      if (!(interceptor instanceof SpinnakerRequestHeaderInterceptor)
          && !(interceptor instanceof Retrofit2EncodeCorrectionInterceptor)) {
        clientBuilder.addInterceptor(interceptor);
      }
    }
    return clientBuilder.build();
  }
}
