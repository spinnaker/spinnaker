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
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class HttpClientUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientUtils.class);
  private static final int MAX_RETRIES = 5;
  private static final int RETRY_INTERVAL = 3000;
  private static final int TIMEOUT_MILLIS = 30000;
  private static List<Integer> RETRYABLE_500_HTTP_STATUS_CODES = Arrays.asList(
    HttpStatus.SC_SERVICE_UNAVAILABLE,
    HttpStatus.SC_INTERNAL_SERVER_ERROR,
    HttpStatus.SC_GATEWAY_TIMEOUT
  );

  private static CloseableHttpClient httpClient = httpClientWithServiceUnavailableRetryStrategy();

  private static CloseableHttpClient httpClientWithServiceUnavailableRetryStrategy() {
    HttpClientBuilder httpClientBuilder =  HttpClients.custom()
      .setServiceUnavailableRetryStrategy(new ServiceUnavailableRetryStrategy() {
        @Override
        public boolean retryRequest(HttpResponse response, int executionCount, HttpContext context) {
          int statusCode = response.getStatusLine().getStatusCode();
          HttpUriRequest currentReq = (HttpUriRequest) context.getAttribute(
            HttpCoreContext.HTTP_REQUEST);

          LOGGER.info("Response code {} for {}", statusCode, currentReq.getURI());
          boolean shouldRetry = (statusCode == 429 || RETRYABLE_500_HTTP_STATUS_CODES.contains(statusCode)) && executionCount <= MAX_RETRIES;
          if (shouldRetry) {
            LOGGER.error("Retrying request on response status {}. Count {} Max is {}", statusCode, executionCount, MAX_RETRIES);
          }

          return shouldRetry;
        }

        @Override
        public long getRetryInterval() {
          return RETRY_INTERVAL;
        }
      }
    );

    httpClientBuilder.setDefaultRequestConfig(
      RequestConfig.custom()
        .setConnectionRequestTimeout(TIMEOUT_MILLIS)
        .setConnectTimeout(TIMEOUT_MILLIS)
        .setSocketTimeout(TIMEOUT_MILLIS)
        .build()
    );

    return httpClientBuilder.build();
  }

  static String httpGetAsString(String url) throws Exception {
    int retries = 0;
    while (true) {
      try {
        try(CloseableHttpResponse response = httpClient.execute(new HttpGet(url))) {
          try (final Reader reader = new InputStreamReader(response.getEntity().getContent())) {
            return CharStreams.toString(reader);
          }
        }
      } catch (Exception e) {
        if (retries++ >= MAX_RETRIES) {
          throw e;
        }

        long timeout = (long) (Math.pow(2, retries) * RETRY_INTERVAL);
        LOGGER.error("Retrying request {} because of network error. Count {} Max is {} wait time {}", url, retries, MAX_RETRIES, timeout);
        Uninterruptibles.sleepUninterruptibly(timeout, TimeUnit.MILLISECONDS);
      }
    }
  }
}
