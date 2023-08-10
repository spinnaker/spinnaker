/*
 * Copyright 2023 Armory.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.config.RestProperties;
import com.netflix.spinnaker.echo.config.RestUrls;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import com.netflix.spinnaker.echo.rest.RestService;
import com.netflix.spinnaker.kork.core.RetrySupport;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestEventListenerTest {

  private RestEventListener listener;
  private Event event;
  private RestService restService;
  private RestEventService restEventService;

  private CircuitBreakerRegistry circuitBreakerRegistry;

  @BeforeEach
  void setup() {
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

    listener =
        new RestEventListener(
            new RestUrls(), new SimpleEventTemplateEngine(), restEventService, new NoopRegistry());

    event = new Event();
    event.setContent(Map.of("uno", "dos"));

    restService = Mockito.mock(RestService.class);

    listener.setEventName("defaultEvent");
    listener.setFieldName("defaultField");
  }

  @Test
  void renderTemplateWhenTemplateIsSet() {
    RestProperties.RestEndpointConfiguration config =
        new RestProperties.RestEndpointConfiguration();
    config.setTemplate("{\"myCustomEventField\":{{event}} }");
    config.setWrap(true);

    RestUrls.Service service =
        RestUrls.Service.builder().client(restService).config(config).build();

    listener.getRestUrls().setServices(List.of(service));

    Map<String, Object> expectedEvent = listener.getMapper().convertValue(event, Map.class);

    listener.processEvent(event);

    Mockito.verify(restService, Mockito.times(1))
        .recordEvent(
            Mockito.argThat(
                it -> {
                  Map<String, Object> expected = new HashMap<>();
                  expected.put("myCustomEventField", expectedEvent);
                  return it.equals(expected);
                }));
  }

  @Test
  void wrapsEventsWhenWrapIsSet() {
    RestProperties.RestEndpointConfiguration config =
        new RestProperties.RestEndpointConfiguration();
    config.setWrap(true);

    RestUrls.Service service =
        RestUrls.Service.builder().client(restService).config(config).build();

    listener.getRestUrls().setServices(List.of(service));

    Map<String, Object> expectedEvent = listener.getMapper().convertValue(event, Map.class);

    listener.processEvent(event);

    Mockito.verify(restService, Mockito.times(1))
        .recordEvent(
            Mockito.argThat(
                it -> {
                  Map<String, Object> expected = new HashMap<>();
                  expected.put("eventName", listener.getEventName());
                  expected.put("defaultField", expectedEvent);
                  return it.equals(expected);
                }));
  }

  @Test
  void canOverwriteWrapFieldFor() {
    RestProperties.RestEndpointConfiguration config =
        new RestProperties.RestEndpointConfiguration();
    config.setWrap(true);
    config.setFieldName("myField");
    config.setEventName("myEventName");

    RestUrls.Service service =
        RestUrls.Service.builder().client(restService).config(config).build();

    listener.getRestUrls().setServices(List.of(service));

    Map<String, Object> expectedEvent = listener.getMapper().convertValue(event, Map.class);

    listener.processEvent(event);

    Mockito.verify(restService, Mockito.times(1))
        .recordEvent(
            Mockito.argThat(
                it -> {
                  Map<String, Object> expected = new HashMap<>();
                  expected.put("eventName", "myEventName");
                  expected.put("myField", expectedEvent);
                  return it.equals(expected);
                }));
  }

  @Test
  void canDisableWrappingOfEvents() {
    RestProperties.RestEndpointConfiguration config =
        new RestProperties.RestEndpointConfiguration();
    config.setWrap(false);

    RestUrls.Service service =
        RestUrls.Service.builder().client(restService).config(config).build();

    listener.getRestUrls().setServices(List.of(service));

    Map<String, Object> expectedEvent = listener.getMapper().convertValue(event, Map.class);

    listener.processEvent(event);

    Mockito.verify(restService, Mockito.times(1)).recordEvent(expectedEvent);
  }

  @Test
  void sendsEventsToMultipleHosts() {
    RestService restService2 = Mockito.mock(RestService.class);

    RestProperties.RestEndpointConfiguration config =
        new RestProperties.RestEndpointConfiguration();
    config.setWrap(false);

    RestUrls.Service service1 =
        RestUrls.Service.builder().client(restService).config(config).build();

    RestUrls.Service service2 =
        RestUrls.Service.builder().client(restService2).config(config).build();

    listener.getRestUrls().setServices(List.of(service1, service2));

    Map<String, Object> expectedEvent = listener.getMapper().convertValue(event, Map.class);

    listener.processEvent(event);

    Mockito.verify(restService, Mockito.times(1)).recordEvent(expectedEvent);
    Mockito.verify(restService2, Mockito.times(1)).recordEvent(expectedEvent);
  }

  @Test()
  void exceptionInSendingEventToOneHostDoesNotAffectSecondHost() {
    RestService restService2 = Mockito.mock(RestService.class);

    RestProperties.RestEndpointConfiguration config =
        new RestProperties.RestEndpointConfiguration();
    config.setWrap(false);
    config.setRetryCount(3);

    RestUrls.Service service1 =
        RestUrls.Service.builder().client(restService).config(config).build();

    RestUrls.Service service2 =
        RestUrls.Service.builder().client(restService2).config(config).build();

    listener.getRestUrls().setServices(List.of(service1, service2));

    Map<String, Object> expectedEvent = listener.getMapper().convertValue(event, Map.class);

    Mockito.when(restService.recordEvent(expectedEvent)).thenThrow(new RuntimeException());

    listener.processEvent(event);

    Mockito.verify(restService, Mockito.times(3)).recordEvent(expectedEvent);
    Assertions.assertThrows(RuntimeException.class, () -> restService.recordEvent(expectedEvent));
    Mockito.verify(restService2, Mockito.times(1)).recordEvent(expectedEvent);
  }

  @Test
  void shouldSendEventWhenCircuitBreakerIsEnabled() {
    RestProperties.RestEndpointConfiguration config =
        new RestProperties.RestEndpointConfiguration();
    config.setCircuitBreakerEnabled(true);
    config.setEventName("circuitBreakerTest");

    RestUrls.Service service =
        RestUrls.Service.builder().client(restService).config(config).build();

    listener.getRestUrls().setServices(List.of(service));

    Map<String, Object> expectedEvent = listener.getMapper().convertValue(event, Map.class);

    listener.processEvent(event);

    Mockito.verify(restService, Mockito.times(1)).recordEvent(expectedEvent);
  }

  /**
   * Testing backwards compatibility. Previously if you enabled the circuit breaker via: {@code
   * rest.circuit-breaker-enabled=true} all rest events used the circuit breaker with the name
   * "sendEvent". This test ensures that users with legacy configs can continue using the circuit
   * breaker
   */
  @Test
  void shouldSendEventWithCircuitBreakerWhenListenerCircuitBreakerFlagIsEnabled() {
    RestProperties.RestEndpointConfiguration config =
        new RestProperties.RestEndpointConfiguration(); // not enabling circuit breaker per service

    RestUrls.Service service =
        RestUrls.Service.builder().client(restService).config(config).build();

    listener.getRestUrls().setServices(List.of(service));
    listener.setCircuitBreakerEnabled(true); // enabling circuit breaker across all rest services

    Map<String, Object> expectedEvent = listener.getMapper().convertValue(event, Map.class);

    listener.processEvent(event);

    Assertions.assertEquals(
        "sendEvent", restEventService.getCircuitBreakerInstance(service).getName());
    Mockito.verify(restService, Mockito.times(1)).recordEvent(expectedEvent);
  }

  @Test
  void shouldNotInvokeObjectMapperWhenCircuitBreakerThrowsCallNotPermittedException() {
    RestProperties.RestEndpointConfiguration config =
        new RestProperties.RestEndpointConfiguration();
    config.setCircuitBreakerEnabled(true);
    config.setEventName("mockedCircuitBreaker");

    RestUrls.Service service =
        RestUrls.Service.builder().client(restService).config(config).build();

    ObjectMapper objectMapper = Mockito.mock(ObjectMapper.class);
    RestEventService mockedRestEventService = Mockito.mock(RestEventService.class);
    listener =
        new RestEventListener(
            new RestUrls(),
            new SimpleEventTemplateEngine(),
            mockedRestEventService,
            new NoopRegistry());
    listener.setMapper(objectMapper);

    listener.getRestUrls().setServices(List.of(service));

    Map<String, Object> expectedEvent =
        EchoObjectMapper.getInstance().convertValue(event, Map.class);

    CircuitBreaker mockedCircuitBreaker = Mockito.mock(CircuitBreaker.class);

    Mockito.when(mockedRestEventService.getCircuitBreakerInstance(service))
        .thenReturn(mockedCircuitBreaker);

    Mockito.doThrow(CallNotPermittedException.class).when(mockedCircuitBreaker).acquirePermission();

    listener.processEvent(event);

    Mockito.verify(objectMapper, Mockito.times(0)).convertValue(event, Map.class);
    Mockito.verify(restService, Mockito.times(0)).recordEvent(expectedEvent);
    Assertions.assertThrows(
        CallNotPermittedException.class, mockedCircuitBreaker::acquirePermission);
  }

  @Test
  void shouldNotInvokeMultipleTimesAfterExceptionWhenCircuitBreakerEnabled() {
    RestProperties.RestEndpointConfiguration config =
        new RestProperties.RestEndpointConfiguration();
    config.setCircuitBreakerEnabled(true);
    config.setEventName("circuitBreakerTest");

    RestUrls.Service service =
        RestUrls.Service.builder().client(restService).config(config).build();

    listener.getRestUrls().setServices(List.of(service));

    Map<String, Object> expectedEvent = listener.getMapper().convertValue(event, Map.class);

    Mockito.when(restService.recordEvent(expectedEvent))
        .thenThrow(new RuntimeException("test exception"));

    listener.processEvent(event);

    Mockito.verify(restService, Mockito.times(1)).recordEvent(expectedEvent);
    Assertions.assertEquals(
        CircuitBreaker.State.OPEN,
        circuitBreakerRegistry.circuitBreaker("circuitBreakerTest").getState());
    Assertions.assertThrows(RuntimeException.class, () -> restService.recordEvent(expectedEvent));

    // reset RestService mock to correctly evaluate that recordEvent is not being called
    Mockito.reset(restService);

    Event newEvent = new Event();
    newEvent.setContent(Map.of("tres", "cuatro"));
    Map<String, Object> newExpectedEvent = listener.getMapper().convertValue(newEvent, Map.class);

    listener.processEvent(newEvent); // process new event
    Mockito.verify(restService, Mockito.times(0)).recordEvent(newExpectedEvent);
  }

  @Test
  void shouldNotTryToSendEventWhenTransformMapThrowsException() {
    RestProperties.RestEndpointConfiguration config =
        new RestProperties.RestEndpointConfiguration();

    RestUrls.Service service =
        RestUrls.Service.builder().client(restService).config(config).build();

    ObjectMapper objectMapper = Mockito.mock(ObjectMapper.class);
    listener.setMapper(objectMapper);

    listener.getRestUrls().setServices(List.of(service));

    Map<String, Object> expectedEvent =
        EchoObjectMapper.getInstance().convertValue(event, Map.class);

    Mockito.when(objectMapper.convertValue(event, Map.class))
        .thenThrow(IllegalArgumentException.class);

    listener.processEvent(event);

    // RestEventListener.transformEventToMap() threw exception and returned null map
    // It shouldn't try to send an empty event
    Mockito.verify(restService, Mockito.times(0)).recordEvent(expectedEvent);
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> objectMapper.convertValue(event, Map.class));
  }

  @Test
  void shouldNotTryToSendEventWhenTransformMapThrowsExceptionCircuitBreakerEnabled() {
    RestProperties.RestEndpointConfiguration config =
        new RestProperties.RestEndpointConfiguration();
    config.setEventName("circuitBreakerTest");
    config.setCircuitBreakerEnabled(true);

    RestUrls.Service service =
        RestUrls.Service.builder().client(restService).config(config).build();

    ObjectMapper objectMapper = Mockito.mock(ObjectMapper.class);
    listener.setMapper(objectMapper);

    listener.getRestUrls().setServices(List.of(service));

    Map<String, Object> expectedEvent =
        EchoObjectMapper.getInstance().convertValue(event, Map.class);

    Mockito.when(objectMapper.convertValue(event, Map.class))
        .thenThrow(new IllegalArgumentException());

    listener.processEvent(event);

    // RestEventListener.transformEventToMap() threw exception and returned null map
    // It shouldn't try to send an empty event
    Mockito.verify(restService, Mockito.times(0)).recordEvent(expectedEvent);
    Assertions.assertEquals(
        CircuitBreaker.State.CLOSED,
        circuitBreakerRegistry.circuitBreaker("circuitBreakerTest").getState());
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> objectMapper.convertValue(event, Map.class));
  }

  @Test()
  void circuitBreakerEnabledForOneHostDoesNotAffectSecondHost() {
    RestProperties.RestEndpointConfiguration config =
        new RestProperties.RestEndpointConfiguration();
    config.setWrap(false);
    config.setEventName("circuitBreakerTest"); // map eventName to circuit breaker instance name
    config.setCircuitBreakerEnabled(true);
    RestUrls.Service service1 =
        RestUrls.Service.builder().client(restService).config(config).build();

    RestService restService2 = Mockito.mock(RestService.class);
    RestProperties.RestEndpointConfiguration config2 =
        new RestProperties.RestEndpointConfiguration();
    config2.setWrap(false);
    config2.setEventName("secondService");
    config2.setCircuitBreakerEnabled(false); // second service does not enable circuit breaker
    RestUrls.Service service2 =
        RestUrls.Service.builder().client(restService2).config(config2).build();

    // add 2 services to listener
    listener.getRestUrls().setServices(List.of(service1, service2));

    Map<String, Object> expectedEvent = listener.getMapper().convertValue(event, Map.class);

    // first service throws Exception when recording event
    Mockito.when(restService.recordEvent(expectedEvent)).thenThrow(new RuntimeException());

    listener.processEvent(event);

    // verify it tried to recordEvent for both services
    Mockito.verify(restService, Mockito.times(1)).recordEvent(expectedEvent);
    Assertions.assertThrows(RuntimeException.class, () -> restService.recordEvent(expectedEvent));
    Mockito.verify(restService2, Mockito.times(1)).recordEvent(expectedEvent);

    // circuitBreakerTest should be open
    Assertions.assertEquals(
        CircuitBreaker.State.OPEN,
        circuitBreakerRegistry.circuitBreaker("circuitBreakerTest").getState());

    // reset RestService mock to correctly evaluate that recordEvent is not being called
    Mockito.reset(restService);
    Mockito.reset(restService2);

    // create new event
    Event newEvent = new Event();
    newEvent.setContent(Map.of("tres", "cuatro"));
    Map<String, Object> newExpectedEvent = listener.getMapper().convertValue(newEvent, Map.class);

    listener.processEvent(newEvent); // process new event

    // first service cannot record event because of OPEN circuit breaker
    Mockito.verify(restService, Mockito.times(0)).recordEvent(newExpectedEvent);
    Mockito.verify(restService2, Mockito.times(1)).recordEvent(newExpectedEvent);
  }
}
