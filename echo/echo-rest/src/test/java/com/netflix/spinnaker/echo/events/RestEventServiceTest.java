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

package com.netflix.spinnaker.echo.events;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.config.RestProperties;
import com.netflix.spinnaker.echo.config.RestUrls;
import com.netflix.spinnaker.echo.rest.RestService;
import com.netflix.spinnaker.echo.util.RetrofitUtils;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.Map;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class RestEventServiceTest {
  private static Map<String, Object> eventMap;
  private static RestEventService restEventService;
  private static RestUrls.Service service;

  static WireMockServer wireMockServer;
  static int port;
  static String baseUrl = "http://localhost:PORT/api";

  @BeforeAll
  static void setup() {
    wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMockServer.start();
    port = wireMockServer.port();
    WireMock.configureFor("localhost", port);

    stubFor(post(urlEqualTo("/api/")).willReturn(aResponse().withStatus(200)));

    baseUrl = baseUrl.replaceFirst("PORT", String.valueOf(port));

    // Create the RestService
    RestService restService =
        new Retrofit.Builder()
            .baseUrl(RetrofitUtils.getBaseUrl(baseUrl))
            .client(new OkHttpClient())
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .addConverterFactory(JacksonConverterFactory.create())
            .build()
            .create(RestService.class);

    // Create a custom configuration for a CircuitBreaker
    CircuitBreakerConfig circuitBreakerConfig =
        CircuitBreakerConfig.custom()
            .failureRateThreshold(1)
            .permittedNumberOfCallsInHalfOpenState(1)
            .minimumNumberOfCalls(1)
            .build();

    // Create a CircuitBreakerRegistry with a custom configuration
    CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);

    // Get the CircuitBreaker from the CircuitBreakerRegistry with a custom configuration
    circuitBreakerRegistry.circuitBreaker("circuitBreakerTest", circuitBreakerConfig);

    restEventService = new RestEventService(new RetrySupport(), circuitBreakerRegistry);

    Event event = new Event();
    event.setContent(Map.of("uno", "dos"));
    ObjectMapper mapper = new ObjectMapper();
    eventMap = mapper.convertValue(event, Map.class);

    service =
        RestUrls.Service.builder()
            .client(restService)
            .config(new RestProperties.RestEndpointConfiguration())
            .build();
  }

  @AfterAll
  static void cleanup() {
    wireMockServer.stop();
  }

  @Test
  void testSendEvent() {
    restEventService.sendEvent(eventMap, service);
    verify(1, postRequestedFor(urlEqualTo("/api/")));
  }
}
