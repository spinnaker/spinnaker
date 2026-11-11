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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.config.RestProperties;
import com.netflix.spinnaker.echo.config.RestUrls;
import com.netflix.spinnaker.echo.rest.RestService;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.util.CustomConverterFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.Map;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import retrofit2.Retrofit;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class RestEventServiceTest {
  private static Map<String, Object> eventMap;
  private static RestEventService restEventService;
  private static RestUrls.Service service;
  private static RestService restService;
  private static CircuitBreakerRegistry circuitBreakerRegistry;

  static WireMockServer wireMockServer;
  static int port;
  // 127.0.0.1 rather than localhost: localhost can resolve to both ::1 and 127.0.0.1, and OkHttp
  // retries a failed request on the other address, which doubles the requests WireMock sees
  static String baseUrl = "http://127.0.0.1:PORT/api";

  @BeforeAll
  static void setup() {
    wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMockServer.start();
    port = wireMockServer.port();
    WireMock.configureFor("localhost", port);

    stubFor(post(urlEqualTo("/api")).willReturn(aResponse().withStatus(200)));

    baseUrl = baseUrl.replaceFirst("PORT", String.valueOf(port));

    // Create the RestService
    restService =
        new Retrofit.Builder()
            .baseUrl("http://not-used.com")
            .client(new OkHttpClient())
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .addConverterFactory(CustomConverterFactory.create())
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
    circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);

    // Get the CircuitBreaker from the CircuitBreakerRegistry with a custom configuration
    circuitBreakerRegistry.circuitBreaker("circuitBreakerTest", circuitBreakerConfig);

    restEventService = new RestEventService(new RetrySupport(), circuitBreakerRegistry);

    Event event = new Event();
    event.setContent(Map.of("uno", "dos"));
    ObjectMapper mapper = JsonMapper.builder().build();
    eventMap = mapper.convertValue(event, Map.class);

    service =
        RestUrls.Service.builder()
            .client(restService)
            .config(new RestProperties.RestEndpointConfiguration().setUrl(baseUrl))
            .build();
  }

  @AfterAll
  static void cleanup() {
    wireMockServer.stop();
  }

  @Test
  void testSendEvent() {
    restEventService.sendEvent(eventMap, service);
    verify(1, postRequestedFor(urlEqualTo("/api")));
  }

  private static RestProperties.RestEndpointConfiguration retryConfig(int retryCount) {
    return new RestProperties.RestEndpointConfiguration().setRetryCount(retryCount);
  }

  // Small backoff so tests using the real RetrySupport do not sleep for seconds
  private static RestProperties.RestEndpointConfiguration fastRetryConfig(int retryCount) {
    return retryConfig(retryCount).setRetryBackoff(Duration.ofMillis(10));
  }

  private RestUrls.Service serviceFor(String path, RestProperties.RestEndpointConfiguration cfg) {
    return RestUrls.Service.builder()
        .client(restService)
        .config(cfg.setUrl(baseUrl.replaceFirst("/api$", "") + path))
        .build();
  }

  private void assertBackoffArgs(
      RestProperties.RestEndpointConfiguration cfg,
      boolean expectedExponential,
      Duration expectedBackoff) {
    RetrySupport retrySupport = Mockito.mock(RetrySupport.class);
    RestEventService svc = new RestEventService(retrySupport, circuitBreakerRegistry);

    svc.sendEvent(eventMap, serviceFor("/args", cfg));

    ArgumentCaptor<Duration> backoff = ArgumentCaptor.forClass(Duration.class);
    Mockito.verify(retrySupport)
        .retry(
            Mockito.any(),
            Mockito.eq(cfg.getRetryCount()),
            backoff.capture(),
            Mockito.eq(expectedExponential));
    assertThat(backoff.getValue()).isEqualTo(expectedBackoff);
  }

  @Test
  void sendEventDefaultsMatchPreviousBehavior() {
    RestProperties.RestEndpointConfiguration defaults =
        new RestProperties.RestEndpointConfiguration();
    assertThat(defaults.getRetryCount()).isEqualTo(1);
    assertThat(defaults.getExponentialBackoff()).isFalse();
    assertThat(defaults.getRetryBackoff()).isEqualTo(Duration.ofMillis(200));
    assertBackoffArgs(retryConfig(5), false, Duration.ofMillis(200));
  }

  @Test
  void sendEventPassesConfiguredRetryBackoffAndExponentialFlag() {
    assertBackoffArgs(
        retryConfig(5).setRetryBackoff(Duration.ofMillis(250)).setExponentialBackoff(true),
        true,
        Duration.ofMillis(250));
  }

  @Test
  void sendEventFallsBackTo200msWhenRetryBackoffIsNull() {
    assertBackoffArgs(retryConfig(5).setRetryBackoff(null), false, Duration.ofMillis(200));
  }

  @Test
  void sendEventUsesFixedBackoffWhenExponentialBackoffIsFalse() {
    assertBackoffArgs(retryConfig(5).setExponentialBackoff(false), false, Duration.ofMillis(200));
  }

  @Test
  void sendEventTreatsNullExponentialBackoffAsFixed() {
    assertBackoffArgs(retryConfig(5).setExponentialBackoff(null), false, Duration.ofMillis(200));
  }

  @Test
  void networkFailuresAreRetriedUntilSuccess() {
    String scenario = "flaky";
    String path = "/flaky";
    stubFor(
        post(urlEqualTo(path))
            .inScenario(scenario)
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
            .willSetStateTo("second"));
    stubFor(
        post(urlEqualTo(path))
            .inScenario(scenario)
            .whenScenarioStateIs("second")
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
            .willSetStateTo("third"));
    stubFor(
        post(urlEqualTo(path))
            .inScenario(scenario)
            .whenScenarioStateIs("third")
            .willReturn(aResponse().withStatus(200)));

    restEventService.sendEvent(eventMap, serviceFor(path, fastRetryConfig(3)));

    verify(3, postRequestedFor(urlEqualTo(path)));
  }

  @Test
  void persistentFailureThrowsAfterRetryCountAttempts() {
    String path = "/down";
    stubFor(
        post(urlEqualTo(path)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    assertThatThrownBy(
            () -> restEventService.sendEvent(eventMap, serviceFor(path, fastRetryConfig(3))))
        .isInstanceOf(SpinnakerException.class);

    verify(3, postRequestedFor(urlEqualTo(path)));
  }

  @Test
  void nonRetryableHttpErrorsAreNotRetried() {
    for (int status : new int[] {400, 404}) {
      String path = "/status" + status;
      stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(status)));

      assertThatThrownBy(
              () -> restEventService.sendEvent(eventMap, serviceFor(path, fastRetryConfig(3))))
          .isInstanceOf(SpinnakerException.class);

      verify(1, postRequestedFor(urlEqualTo(path)));
    }
  }
}
