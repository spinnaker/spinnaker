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
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.protocol.RedirectStrategy;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class HttpClientUtils {

  private final CloseableHttpClient httpClient;
  private final UserConfiguredUrlRestrictions urlRestrictions;
  private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientUtils.class);
  private static final String JVM_HTTP_PROXY_HOST = "http.proxyHost";
  private static final String JVM_HTTP_PROXY_PORT = "http.proxyPort";
  private static final String LOCATION_HEADER = "Location";
  private static final List<Integer> RETRYABLE_500_HTTP_STATUS_CODES =
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
      httpClientBuilder.setRetryStrategy(
          new HttpRequestRetryStrategy() {
            // Called for I/O exceptions (network errors), previously was part of retry handler
            // logic
            @Override
            public boolean retryRequest(
                final HttpRequest request,
                final IOException exception,
                final int execCount,
                final HttpContext context) {

              // Sleep between attempts (preserve original behavior)
              Uninterruptibles.sleepUninterruptibly(
                  httpClientProperties.getRetryInterval(), TimeUnit.MILLISECONDS);

              String uri = request.getRequestUri();
              LOGGER.info(
                  "Encountered network error. Retrying request {},  Count {} Max is {}",
                  uri,
                  execCount,
                  httpClientProperties.getMaxRetryAttempts());
              return execCount <= httpClientProperties.getMaxRetryAttempts();
            }

            // Called for responses (status codes)
            @Override
            public boolean retryRequest(
                final HttpResponse response, final int execCount, final HttpContext context) {

              int statusCode = response.getCode();

              HttpRequest currentReq =
                  (HttpRequest) context.getAttribute(HttpCoreContext.HTTP_REQUEST);

              LOGGER.info("Response code {} for {}", statusCode, currentReq.getRequestUri());

              if (statusCode >= HttpStatus.SC_OK && statusCode <= 299) {
                return false;
              }
              boolean shouldRetry =
                  (statusCode == 429 || RETRYABLE_500_HTTP_STATUS_CODES.contains(statusCode))
                      && execCount <= httpClientProperties.getMaxRetryAttempts();

              // Redirect detection & validation (preserve original behavior)
              try {
                RedirectStrategy laxRedirectStrategy = new CustomLaxRedirectStrategy();

                boolean isRedirected =
                    // HttpRequest implementations (ClassicHttpRequest) implement HttpRequest
                    laxRedirectStrategy.isRedirected(currentReq, response, context);

                if (isRedirected) {
                  // verify that we are not redirecting to a restricted url
                  if (response.getFirstHeader(LOCATION_HEADER) != null) {
                    String redirectLocation = response.getFirstHeader(LOCATION_HEADER).getValue();
                    urlRestrictions.validateURI(redirectLocation);

                    LOGGER.info(
                        "Attempt redirect from {} to {} ",
                        currentReq.getRequestUri(),
                        redirectLocation);

                    // set redirect strategy for future execution (builder is used to build client)
                    httpClientBuilder.setRedirectStrategy(laxRedirectStrategy);

                    // Don't allow retry for redirection
                    return false;
                  }
                }
              } catch (ProtocolException protocolException) {
                LOGGER.error(
                    "Failed redirect from {} to {}. Error: {}",
                    currentReq.getRequestUri(),
                    response.getFirstHeader(LOCATION_HEADER).getValue(),
                    protocolException.getMessage());
              } catch (Exception e) {
                LOGGER.error("Unexpected redirect handling error: {}", e.getMessage());
              }

              if (!shouldRetry) {
                throw new RetryRequestException(
                    String.format(
                        "Not retrying %s. Count %d, Max %d",
                        currentReq.getRequestUri(),
                        execCount,
                        httpClientProperties.getMaxRetryAttempts()));
              }

              LOGGER.error(
                  "Retrying request on response status {}. Count {} Max is {}",
                  statusCode,
                  execCount,
                  httpClientProperties.getMaxRetryAttempts());
              return true;
            }

            @Override
            public TimeValue getRetryInterval(
                HttpResponse response, int execCount, HttpContext context) {
              return TimeValue.ofMilliseconds(httpClientProperties.getRetryInterval());
            }
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
      httpClientBuilder.setProxy(new HttpHost("http", proxyHostname, proxyPort));
    }

    // Request config: timeouts
    RequestConfig requestConfig =
        RequestConfig.custom()
            .setConnectionRequestTimeout(
                Timeout.ofMilliseconds(httpClientProperties.getTimeoutMillis()))
            .setConnectTimeout(Timeout.ofMilliseconds(httpClientProperties.getTimeoutMillis()))
            .setResponseTimeout(Timeout.ofMilliseconds(httpClientProperties.getTimeoutMillis()))
            .build();
    httpClientBuilder.setDefaultRequestConfig(requestConfig);

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

  static class RetryRequestException extends RuntimeException {
    RetryRequestException(String cause) {
      super(cause);
    }
  }

  /**
   * Custom LaxRedirectStrategy that mimics the old HttpClient4 LaxRedirectStrategy behavior: -
   * considers 301/302 as redirectable (and checks for Location header) - defers to
   * DefaultRedirectStrategy for other cases
   */
  static class CustomLaxRedirectStrategy implements RedirectStrategy {

    private final DefaultRedirectStrategy delegate = new DefaultRedirectStrategy();

    @Override
    public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) {
      int statusCode = response.getCode();

      if (statusCode == HttpStatus.SC_MOVED_PERMANENTLY
          || statusCode == HttpStatus.SC_MOVED_TEMPORARILY) {

        return response.getFirstHeader(LOCATION_HEADER).getValue() != null
            && !response.getFirstHeader(LOCATION_HEADER).getValue().isEmpty();
      }
      return false;
    }

    @Override
    public URI getLocationURI(
        final HttpRequest request, final HttpResponse response, final HttpContext context)
        throws HttpException {
      return delegate.getLocationURI(request, response, context);
    }
  }
}
