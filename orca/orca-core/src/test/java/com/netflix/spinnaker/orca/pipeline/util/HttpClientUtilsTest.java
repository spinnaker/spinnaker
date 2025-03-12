/*
 * Copyright 2022 Armory, Inc.
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

package com.netflix.spinnaker.orca.pipeline.util;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Fail.fail;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.netflix.spinnaker.orca.config.UserConfiguredUrlRestrictions;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class HttpClientUtilsTest {

  private final String host = "localhost";
  private final int port = 8080;
  private final String resource = "/v1/text";
  private final String redirectUrl = "https://spinnaker.io/";
  private final UserConfiguredUrlRestrictions.Builder config =
      new UserConfiguredUrlRestrictions.Builder();

  WireMockServer wireMockServer = new WireMockServer();

  @BeforeEach
  public void setup() {
    wireMockServer.start();
    configureFor(host, port);
  }

  @AfterEach
  public void destroy() {
    wireMockServer.stop();
  }

  @Test
  public void testZeroRetryFor503StatusCode() {
    // given:
    config.setHttpClientProperties(
        UserConfiguredUrlRestrictions.HttpClientProperties.builder().enableRetry(false).build());
    HttpClientUtils httpClientUtils = new HttpClientUtils(config.build());
    stubFor(get(resource).willReturn(aResponse().withStatus(503)));

    // when:
    try {
      httpClientUtils.httpGetAsString(String.format("http://%s:%s%s", host, port, resource));
    } catch (IOException e) {
      fail("Should not have thrown an exception");
    }

    // then:
    verify(1, getRequestedFor(urlEqualTo(resource)));
  }

  @Test
  public void testDefaultRetryForSocketException() {
    // given:
    HttpClientUtils httpClientUtils = new HttpClientUtils(config.build());
    stubFor(get(resource).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    // when:
    try {
      httpClientUtils.httpGetAsString(String.format("http://%s:%s%s", host, port, resource));
    } catch (IOException | HttpClientUtils.RetryRequestException ignored) {

    }

    // then:
    verify(2, getRequestedFor(urlEqualTo(resource)));
  }

  @Test
  public void testTwoRetryForSocketException() {
    // given:
    config.setHttpClientProperties(
        UserConfiguredUrlRestrictions.HttpClientProperties.builder()
            .enableRetry(true)
            .maxRetryAttempts(2)
            .build());
    HttpClientUtils httpClientUtils = new HttpClientUtils(config.build());
    stubFor(get(resource).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    // when:
    try {
      httpClientUtils.httpGetAsString(String.format("http://%s:%s%s", host, port, resource));
    } catch (IOException | HttpClientUtils.RetryRequestException ignored) {

    }

    // then:
    verify(3, getRequestedFor(urlEqualTo(resource)));
  }

  @ParameterizedTest
  @ValueSource(ints = {301, 302})
  public void testRedirectWithRetryEnabled(int httpStatus) throws IOException {
    // Set up the configuration to enable retry functionality
    config.setHttpClientProperties(
        UserConfiguredUrlRestrictions.HttpClientProperties.builder().enableRetry(true).build());
    // Create the HttpClientUtils instance with the configuration
    HttpClientUtils httpClientUtils = new HttpClientUtils(config.build());

    stubFor(
        get(resource)
            .willReturn(aResponse().withStatus(httpStatus).withHeader("Location", redirectUrl)));

    // when:
    String response =
        httpClientUtils.httpGetAsString(String.format("http://%s:%s%s", host, port, resource));

    // then:
    assertNotNull(response);
    verify(1, getRequestedFor(urlEqualTo(resource)));
  }

  @ParameterizedTest
  @ValueSource(ints = {301, 302})
  public void testRedirectWithRetryDisabled(int httpStatus) throws IOException {
    // Set up the configuration to enable retry functionality
    config.setHttpClientProperties(
        UserConfiguredUrlRestrictions.HttpClientProperties.builder().enableRetry(false).build());
    // Create the HttpClientUtils instance with the configuration
    HttpClientUtils httpClientUtils = new HttpClientUtils(config.build());

    stubFor(
        get(resource)
            .willReturn(aResponse().withStatus(httpStatus).withHeader("Location", redirectUrl)));

    // when:
    String response =
        httpClientUtils.httpGetAsString(String.format("http://%s:%s%s", host, port, resource));

    // then:
    assertNotNull(response);
    verify(1, getRequestedFor(urlEqualTo(resource)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"192.168.1.1", "127.0.0.1"})
  void testRedirectThrowsRetryRequestExceptionForInvalidUrl(String invalidUrl) {
    // Set up the configuration to enable retry functionality
    config.setHttpClientProperties(
        UserConfiguredUrlRestrictions.HttpClientProperties.builder().enableRetry(true).build());
    // Create the HttpClientUtils instance with the configuration
    HttpClientUtils httpClientUtils = new HttpClientUtils(config.build());

    stubFor(
        get(invalidUrl)
            .willReturn(aResponse().withStatus(301).withHeader("Location", redirectUrl)));

    // when & then
    assertThrows(
        HttpClientUtils.RetryRequestException.class,
        () -> {
          httpClientUtils.httpGetAsString(String.format("http://%s:%s%s", host, port, resource));
        });
  }
}
