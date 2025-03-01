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

package com.netflix.spinnaker.echo.events;

import com.netflix.spinnaker.echo.config.RestProperties;
import com.netflix.spinnaker.echo.config.RestUrls;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import net.logstash.logback.encoder.org.apache.commons.lang.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty("rest.enabled")
public class RestEventService {

  private final RetrySupport retrySupport;
  private final CircuitBreakerRegistry circuitBreakerRegistry;

  /**
   * Sends an event to a REST service using a Circuit Breaker.
   *
   * @param eventMap The event data to be sent.
   * @param service The REST service to which the event should be sent.
   */
  public void sendEventWithCircuitBreaker(
      Map<String, Object> eventMap, RestUrls.Service service, CircuitBreaker circuitBreaker) {
    circuitBreaker.executeRunnable(() -> sendEvent(eventMap, service));
  }

  /**
   * Sends an event to a REST service with retry support.
   *
   * @param event The event data to be sent.
   * @param service The REST service to which the event should be sent.
   */
  public void sendEvent(Map<String, Object> event, RestUrls.Service service) {
    retrySupport.retry(
        () -> Retrofit2SyncCall.execute(service.getClient().recordEvent(event)),
        service.getConfig().getRetryCount(),
        Duration.ofMillis(200),
        false);
  }

  /**
   * Retrieves or creates a Circuit Breaker instance for a specific REST service.
   *
   * <p>Your service event name {@link RestProperties.RestEndpointConfiguration#getEventName()}
   * should map to the Circuit Breaker instance name {@link CircuitBreaker#getName()}
   *
   * @param service The REST service for which the Circuit Breaker instance is requested.
   * @return The Circuit Breaker instance.
   */
  public CircuitBreaker getCircuitBreakerInstance(RestUrls.Service service) {
    String circuitBreakerInstance =
        StringUtils.defaultString(service.getConfig().getEventName(), "sendEvent");

    return circuitBreakerRegistry.circuitBreaker(circuitBreakerInstance);
  }
}
