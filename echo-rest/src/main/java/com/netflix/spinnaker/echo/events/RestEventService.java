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

import com.netflix.spinnaker.echo.config.RestUrls;
import com.netflix.spinnaker.kork.core.RetrySupport;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty("rest.enabled")
public class RestEventService {

  private final RetrySupport retrySupport;

  @CircuitBreaker(name = "sendEvent")
  public void sendEventWithCircuitBreaker(Map<String, Object> event, RestUrls.Service service) {
    sendEvent(event, service);
  }

  public void sendEvent(Map<String, Object> event, RestUrls.Service service) {
    retrySupport.retry(
        () -> service.getClient().recordEvent(event),
        service.getConfig().getRetryCount(),
        Duration.ofMillis(200),
        false);
  }
}
