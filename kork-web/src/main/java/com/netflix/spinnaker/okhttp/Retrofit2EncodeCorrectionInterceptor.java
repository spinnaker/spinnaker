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

package com.netflix.spinnaker.okhttp;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * An {@link Interceptor} implementation that corrects the partial encoding done by Retrofit2.
 *
 * <p>This interceptor processes the URL path and query parameters of HTTP requests to ensure that
 * they are correctly encoded. Retrofit2 performs partial encoding, which may result in incorrect
 * URL encoding for certain characters. This interceptor addresses this by decoding and re-encoding
 * the path segments and query parameters.
 *
 * <p>The behavior of this interceptor can be controlled via the {@code skipEncodingCorrection}
 * flag. When this flag is set to {@code true}, the interceptor will bypass the encoding correction
 * process and simply forward the request as-is.
 *
 * <p>Refer <a
 * href="https://github.com/spinnaker/spinnaker/issues/7021">spinnaker/spinnaker/issues/7021</a> and
 * <a href="https://github.com/square/retrofit/issues/4312">square/retrofit/issues/4312</a> for more
 * details
 */
public class Retrofit2EncodeCorrectionInterceptor implements Interceptor {

  private final boolean skipEncodingCorrection;

  public Retrofit2EncodeCorrectionInterceptor() {
    this.skipEncodingCorrection = false;
  }

  public Retrofit2EncodeCorrectionInterceptor(boolean skipEncodingCorrection) {
    this.skipEncodingCorrection = skipEncodingCorrection;
  }

  @Override
  public Response intercept(Interceptor.Chain chain) throws IOException {
    if (skipEncodingCorrection) {
      return chain.proceed(chain.request());
    }

    Request originalRequest = chain.request();
    HttpUrl originalUrl = originalRequest.url();
    HttpUrl.Builder newUrlBuilder = originalUrl.newBuilder();

    // Decode and encode the path to correct the partial encoding done by retrofit2
    for (int i = 0; i < originalUrl.pathSize(); i++) {
      String retrofit2EncodedSegment = originalUrl.encodedPathSegments().get(i);
      String encodedPathSegmentAfterCorrection =
          processRetrofit2EncodedString(retrofit2EncodedSegment);
      newUrlBuilder.setEncodedPathSegment(i, encodedPathSegmentAfterCorrection);
    }

    // Decode and encode the query parameters to correct the partial encoding done by retrofit2
    for (String paramName : originalUrl.queryParameterNames()) {
      String retrofit2EncodedParam = getEncodedQueryParam(originalUrl, paramName);
      if (retrofit2EncodedParam != null) {
        String encodedParam = processRetrofit2EncodedString(retrofit2EncodedParam);
        newUrlBuilder.setEncodedQueryParameter(paramName, encodedParam);
      }
    }

    Request newRequest = originalRequest.newBuilder().url(newUrlBuilder.build()).build();

    return chain.proceed(newRequest);
  }

  /**
   * Gets the encoded query parameter for the given {@code paramName} from the given {@link
   * HttpUrl}.
   *
   * <p>This method extracts the retrofit2-encoded query parameter from the given URL and returns
   * its value as a string.
   *
   * @param url the {@link HttpUrl} to extract the query parameter from
   * @param paramName the name of the query parameter to extract
   * @return the encoded query parameter value, or {@code null} if the parameter is not present
   */
  private String getEncodedQueryParam(HttpUrl url, String paramName) {
    String encodedQuery = url.encodedQuery();
    if (encodedQuery == null) {
      return null;
    }

    for (String pair : encodedQuery.split("&")) {
      String[] parts = pair.split("=", 2);
      if (parts.length == 2 && parts[0].equals(paramName)) {
        return parts[1];
      }
    }
    return null;
  }

  /**
   * Fixes the partial encoding of strings done by retrofit2 by replacing all the '%' characters
   * that are not followed by two hexadecimal digits(i.e. real % character rather than a part of an
   * encoded representation of any special character) with '%25'. This is needed because the {@link
   * URLDecoder#decode(String, String)} method will throw an exception if the input string contains
   * un-encoded '%' characters.
   *
   * <p>This method will also replace all the '+' characters with '%2B' to preserve them, since the
   * {@link URLDecoder#decode(String, String)} method will replace '+' with space.
   *
   * @param retrofit2EncodedString the string as encoded by retrofit2
   * @return the fully encoded string
   */
  private String processRetrofit2EncodedString(String retrofit2EncodedString) {
    String retrofit2EncodedVal =
        retrofit2EncodedString.replaceAll("%(?![0-9A-Fa-f]{2})", "%25").replaceAll("\\+", "%2B");
    String decodedVal = URLDecoder.decode(retrofit2EncodedVal, StandardCharsets.UTF_8);
    String encodedString = URLEncoder.encode(decodedVal, StandardCharsets.UTF_8);
    return encodedString.replace("+", "%20");
  }
}
