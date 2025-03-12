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

package com.netflix.spinnaker.echo.util;

import okhttp3.HttpUrl;

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
}
