/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.pipeline.util;

import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.Uninterruptibles;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpClientUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientUtils.class);
  private static final int MAX_RETRIES = 5;
  private static final int RETRY_INTERVAL = 5000;
  private static final int TIMEOUT_MILLIS = 30000;
  private static final String JVM_HTTP_PROXY_HOST = "http.proxyHost";
  private static final String JVM_HTTP_PROXY_PORT = "http.proxyPort";
  private static List<Integer> RETRYABLE_500_HTTP_STATUS_CODES =
      Arrays.asList(
          HttpStatus.SC_SERVICE_UNAVAILABLE,
          HttpStatus.SC_INTERNAL_SERVER_ERROR,
          HttpStatus.SC_GATEWAY_TIMEOUT);

  private static CloseableHttpClient httpClient = httpClientWithServiceUnavailableRetryStrategy();

  private static CloseableHttpClient httpClientWithServiceUnavailableRetryStrategy() {
    HttpClientBuilder httpClientBuilder =
        HttpClients.custom()
            .setServiceUnavailableRetryStrategy(
                new ServiceUnavailableRetryStrategy() {
                  @Override
                  public boolean retryRequest(
                      HttpResponse response, int executionCount, HttpContext context) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    HttpUriRequest currentReq =
                        (HttpUriRequest) context.getAttribute(HttpCoreContext.HTTP_REQUEST);
                    LOGGER.info("Response code {} for {}", statusCode, currentReq.getURI());

                    if (statusCode >= HttpStatus.SC_OK && statusCode <= 299) {
                      return false;
                    }

                    boolean shouldRetry =
                        (statusCode == 429 || RETRYABLE_500_HTTP_STATUS_CODES.contains(statusCode))
                            && executionCount <= MAX_RETRIES;
                    if (!shouldRetry) {
                      throw new RetryRequestException(
                          String.format(
                              "Not retrying %s. Count %d, Max %d",
                              currentReq.getURI(), executionCount, MAX_RETRIES));
                    }

                    LOGGER.error(
                        "Retrying request on response status {}. Count {} Max is {}",
                        statusCode,
                        executionCount,
                        MAX_RETRIES);
                    return true;
                  }

                  @Override
                  public long getRetryInterval() {
                    return RETRY_INTERVAL;
                  }
                });

    httpClientBuilder.setRetryHandler(
        (exception, executionCount, context) -> {
          HttpUriRequest currentReq =
              (HttpUriRequest) context.getAttribute(HttpCoreContext.HTTP_REQUEST);
          Uninterruptibles.sleepUninterruptibly(RETRY_INTERVAL, TimeUnit.MILLISECONDS);
          LOGGER.info(
              "Encountered network error. Retrying request {},  Count {} Max is {}",
              currentReq.getURI(),
              executionCount,
              MAX_RETRIES);
          return executionCount <= MAX_RETRIES;
        });

    String proxyHostname = System.getProperty(JVM_HTTP_PROXY_HOST);
    if (proxyHostname != null) {
      int proxyPort =
          System.getProperty(JVM_HTTP_PROXY_PORT) != null
              ? Integer.parseInt(System.getProperty(JVM_HTTP_PROXY_PORT))
              : 8080;
      LOGGER.info(
          "Found system properties for proxy configuration. Setting up http client to use proxy with "
              + "hostname {} and port {}",
          proxyHostname,
          proxyPort);
      httpClientBuilder.setProxy(new HttpHost(proxyHostname, proxyPort, "http"));
    }

    httpClientBuilder.setDefaultRequestConfig(
        RequestConfig.custom()
            .setConnectionRequestTimeout(TIMEOUT_MILLIS)
            .setConnectTimeout(TIMEOUT_MILLIS)
            .setSocketTimeout(TIMEOUT_MILLIS)
            .build());

    return httpClientBuilder.build();
  }

  public static String httpGetAsString(String url) throws IOException {
    try (CloseableHttpResponse response = httpClient.execute(new HttpGet(url))) {
      try (final Reader reader = new InputStreamReader(response.getEntity().getContent())) {
        return CharStreams.toString(reader);
      }
    }
  }

  static class RetryRequestException extends RuntimeException {
    RetryRequestException(String cause) {
      super(cause);
    }
  }
}
