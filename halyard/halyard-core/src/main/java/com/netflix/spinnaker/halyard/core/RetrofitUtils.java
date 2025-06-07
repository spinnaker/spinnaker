package com.netflix.spinnaker.halyard.core;

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
