/*
 * Copyright 2020 Armory, Inc.
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

package com.netflix.kayenta.datadog.functional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.netflix.kayenta.datadog.config.DatadogConfiguration;
import com.netflix.kayenta.datadog.service.DatadogRemoteService;
import com.netflix.kayenta.datadog.service.DatadogTimeSeries;
import com.netflix.kayenta.model.DatadogMetricDescriptorsResponse;
import com.netflix.kayenta.retrofit.config.RemoteService;
import com.netflix.kayenta.retrofit.config.RetrofitClientFactory;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import com.squareup.okhttp.OkHttpClient;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.netty.MockServer;
import org.slf4j.LoggerFactory;

/** TDD for https://github.com/spinnaker/kayenta/issues/684 */
public class DatadogSecretsDoNotLeakWhenApiCalledFunctionalTest {

  private static final String API_KEY = "IAMASECRETANDSHOULDNOTBELOGGED";
  private static final String APPLICATION_KEY = "IAMASECRETANDALSOSHOULDNOTBELOGGED";
  private static final List<String> SECRETS = ImmutableList.of(API_KEY, APPLICATION_KEY);
  public MockServer mockServer;
  private ListAppender<ILoggingEvent> listAppender;

  private MockServerClient mockServerClient;
  private ObjectMapper objectMapper;

  DatadogRemoteService datadogRemoteService;

  @BeforeEach
  public void before() {
    mockServer = new MockServer();
    mockServerClient = new MockServerClient("localhost", mockServer.getPort());
    listAppender = new ListAppender<>();
    Logger mockLogger =
        (Logger) LoggerFactory.getLogger("DatadogSecretsDoNotLeakWhenApiCalledFunctionalTest");
    mockLogger.addAppender(listAppender);
    listAppender.start();

    RetrofitClientFactory retrofitClientFactory = new RetrofitClientFactory();
    retrofitClientFactory.retrofitLogLevel = "BASIC";
    retrofitClientFactory.createRetrofitLogger =
        (type) -> {
          return new Slf4jRetrofitLogger(mockLogger);
        };

    objectMapper = new ObjectMapper();
    datadogRemoteService =
        DatadogConfiguration.createDatadogRemoteService(
            retrofitClientFactory,
            objectMapper,
            new RemoteService().setBaseUrl("http://localhost:" + mockServer.getPort()),
            new OkHttpClient());
  }

  @AfterEach
  public void cleanup() {
    mockServer.close();
    mockServer.stop();
  }

  @Test
  public void
      test_that_the_datadog_remote_service_does_not_log_the_api_key_when_getTimeSeries_is_called() {
    DatadogTimeSeries mockResponse = new DatadogTimeSeries();
    String mockResponseAsString;
    try {
      mockResponseAsString = objectMapper.writeValueAsString(mockResponse);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize mock DatadogTimeSeries response");
    }
    mockServerClient
        .when(HttpRequest.request())
        .respond(HttpResponse.response(mockResponseAsString));

    Instant now = Instant.now();
    DatadogTimeSeries res =
        datadogRemoteService.getTimeSeries(
            API_KEY,
            APPLICATION_KEY,
            (int) now.minus(5, ChronoUnit.MINUTES).toEpochMilli(),
            (int) now.toEpochMilli(),
            "some query");

    assertEquals(mockResponse, res);
    assertTrue(listAppender.list.size() > 0, "We expected there to be at least 1 logged message");
    assertMessagesDoNotContainSecrets(listAppender.list);
  }

  @Test
  public void
      test_that_the_datadog_remote_service_does_not_log_the_api_key_when_getMetrics_is_called() {
    DatadogMetricDescriptorsResponse mockResponse = new DatadogMetricDescriptorsResponse();
    String mockResponseAsString;
    try {
      mockResponseAsString = objectMapper.writeValueAsString(mockResponse);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize mock DatadogTimeSeries response");
    }

    mockServerClient
        .when(HttpRequest.request())
        .respond(HttpResponse.response(mockResponseAsString));

    DatadogMetricDescriptorsResponse res =
        datadogRemoteService.getMetrics(
            API_KEY, APPLICATION_KEY, Instant.now().minus(5, ChronoUnit.MINUTES).toEpochMilli());

    assertEquals(mockResponse, res);
    assertTrue(listAppender.list.size() > 0, "We expected there to be at least 1 logged message");
    assertMessagesDoNotContainSecrets(listAppender.list);
  }

  private void assertMessagesDoNotContainSecrets(List<ILoggingEvent> events) {
    List<String> matchedLogStatements =
        events.stream()
            .filter(
                event -> SECRETS.stream().anyMatch(secret -> event.getMessage().contains(secret)))
            .map(ILoggingEvent::getMessage)
            .collect(Collectors.toList());

    if (matchedLogStatements.size() > 0) {
      StringBuilder msg =
          new StringBuilder(
              "The log messages should not contain secrets, but the following messages had secrets");
      matchedLogStatements.forEach(m -> msg.append('\n').append(m));
      fail(msg.toString());
    }
  }
}
