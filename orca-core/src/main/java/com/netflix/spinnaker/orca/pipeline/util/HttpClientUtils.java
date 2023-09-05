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
import com.netflix.spinnaker.orca.config.UserConfiguredUrlRestrictions;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolException;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class HttpClientUtils {

  private CloseableHttpClient httpClient;
  private final UserConfiguredUrlRestrictions urlRestrictions;
  private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientUtils.class);
  private static final String JVM_HTTP_PROXY_HOST = "http.proxyHost";
  private static final String JVM_HTTP_PROXY_PORT = "http.proxyPort";
  private static final String LOCATION_HEADER = "Location";
  private static List<Integer> RETRYABLE_500_HTTP_STATUS_CODES =
      Arrays.asList(
          HttpStatus.SC_SERVICE_UNAVAILABLE,
          HttpStatus.SC_INTERNAL_SERVER_ERROR,
          HttpStatus.SC_GATEWAY_TIMEOUT);

  public HttpClientUtils(UserConfiguredUrlRestrictions userConfiguredUrlRestrictions) {
    this.httpClient = create(userConfiguredUrlRestrictions.getHttpClientProperties());
    this.urlRestrictions = userConfiguredUrlRestrictions;
  }

  private CloseableHttpClient create(
      UserConfiguredUrlRestrictions.HttpClientProperties httpClientProperties) {
    HttpClientBuilder httpClientBuilder = HttpClients.custom();

    if (httpClientProperties.isEnableRetry()) {
      httpClientBuilder.setServiceUnavailableRetryStrategy(
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
                      && executionCount <= httpClientProperties.getMaxRetryAttempts();

              try {
                RedirectStrategy laxRedirectStrategy =
                    new CustomRedirectStrategy().getLaxRedirectStrategy();

                boolean isRedirected =
                    laxRedirectStrategy.isRedirected(currentReq, response, context);

                if (isRedirected) {
                  // verify that we are not redirecting to a restricted url
                  String redirectLocation = response.getFirstHeader(LOCATION_HEADER).getValue();
                  urlRestrictions.validateURI(redirectLocation);

                  LOGGER.info(
                      "Attempt redirect from {} to {} ", currentReq.getURI(), redirectLocation);

                  httpClientBuilder.setRedirectStrategy(laxRedirectStrategy).build();
                  return false; // Don't allow retry for redirection
                }
              } catch (ProtocolException protocolException) {
                LOGGER.error(
                    "Failed redirect from {} to {}. Error: {}",
                    currentReq.getRequestLine().getUri(),
                    response.getFirstHeader(LOCATION_HEADER).getValue(),
                    protocolException.getMessage());
              }
              if (!shouldRetry) {
                throw new RetryRequestException(
                    String.format(
                        "Not retrying %s. Count %d, Max %d",
                        currentReq.getURI(),
                        executionCount,
                        httpClientProperties.getMaxRetryAttempts()));
              }

              LOGGER.error(
                  "Retrying request on response status {}. Count {} Max is {}",
                  statusCode,
                  executionCount,
                  httpClientProperties.getMaxRetryAttempts());
              return true;
            }

            @Override
            public long getRetryInterval() {
              return httpClientProperties.getRetryInterval();
            }
          });

      httpClientBuilder.setRetryHandler(
          (exception, executionCount, context) -> {
            HttpUriRequest currentReq =
                (HttpUriRequest) context.getAttribute(HttpCoreContext.HTTP_REQUEST);
            Uninterruptibles.sleepUninterruptibly(
                httpClientProperties.getRetryInterval(), TimeUnit.MILLISECONDS);
            LOGGER.info(
                "Encountered network error. Retrying request {},  Count {} Max is {}",
                currentReq.getURI(),
                executionCount,
                httpClientProperties.getMaxRetryAttempts());
            return executionCount <= httpClientProperties.getMaxRetryAttempts();
          });
    } else {
      httpClientBuilder.disableAutomaticRetries();
    }

    String proxyHostname = System.getProperty(JVM_HTTP_PROXY_HOST);
    if (proxyHostname != null) {
      int proxyPort = getProxyPort();
      LOGGER.info(
          "Found system properties for proxy configuration. Setting up http client to use proxy with "
              + "hostname {} and port {}",
          proxyHostname,
          proxyPort);
      httpClientBuilder.setProxy(new HttpHost(proxyHostname, proxyPort, "http"));
    }

    httpClientBuilder.setDefaultRequestConfig(
        RequestConfig.custom()
            .setConnectionRequestTimeout(httpClientProperties.getTimeoutMillis())
            .setConnectTimeout(httpClientProperties.getTimeoutMillis())
            .setSocketTimeout(httpClientProperties.getTimeoutMillis())
            .build());

    return httpClientBuilder.build();
  }

  public String httpGetAsString(String url) throws IOException {
    try (CloseableHttpResponse response = httpClient.execute(new HttpGet(url))) {
      try (final Reader reader = new InputStreamReader(response.getEntity().getContent())) {
        return CharStreams.toString(reader);
      }
    }
  }

  private int getProxyPort() {
    String proxyPort = System.getProperty(JVM_HTTP_PROXY_PORT);
    int defaultProxyPort = 8080;

    if (proxyPort != null) {
      try {
        return Integer.parseInt(proxyPort);
      } catch (NumberFormatException e) {
        LOGGER.warn(
            "Invalid proxy port number: {}. Using default port: {}", proxyPort, defaultProxyPort);
      }
    }
    return defaultProxyPort;
  }

  class RetryRequestException extends RuntimeException {
    RetryRequestException(String cause) {
      super(cause);
    }
  }

  class CustomRedirectStrategy {
    public RedirectStrategy getLaxRedirectStrategy() {
      return new LaxRedirectStrategy() {
        @Override
        public boolean isRedirected(
            HttpRequest request, HttpResponse response, HttpContext context) {
          int statusCode = response.getStatusLine().getStatusCode();

          if (statusCode == HttpStatus.SC_MOVED_PERMANENTLY
              || statusCode == HttpStatus.SC_MOVED_TEMPORARILY) {

            return response.getFirstHeader(LOCATION_HEADER).getValue() != null
                && !response.getFirstHeader(LOCATION_HEADER).getValue().isEmpty();
          }
          return false;
        }
      };
    }
  }
}
