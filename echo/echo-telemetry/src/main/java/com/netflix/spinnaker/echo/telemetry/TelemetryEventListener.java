/*
 * Copyright 2019 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.echo.telemetry;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.util.JsonFormat;
import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.api.events.EventListener;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(value = "stats.enabled", matchIfMissing = true)
public class TelemetryEventListener implements EventListener {

  protected static final String TELEMETRY_REGISTRY_NAME = "telemetry";

  private static final Set<String> LOGGABLE_DETAIL_TYPES =
      ImmutableSet.of(
          "orca:orchestration:complete",
          "orca:orchestration:failed",
          "orca:pipeline:complete",
          "orca:pipeline:failed");

  private static final JsonFormat.Printer JSON_PRINTER =
      JsonFormat.printer().includingDefaultValueFields();

  private final TelemetryService telemetryService;

  private final CircuitBreakerRegistry registry;

  private final List<TelemetryEventDataProvider> dataProviders;

  @Autowired
  public TelemetryEventListener(
      TelemetryService telemetryService,
      CircuitBreakerRegistry registry,
      List<TelemetryEventDataProvider> dataProviders) {
    this.telemetryService = telemetryService;
    this.registry = registry;
    this.dataProviders = dataProviders;
  }

  @Override
  public void processEvent(Event event) {

    // These preconditions are also guaranteed to TelemetryEventDataProvider, so don't change them
    // without checking the implementations.
    if (event.getDetails() == null || event.getContent() == null) {
      log.debug("Telemetry not sent: Details or content not found in event");
      return;
    }

    String eventType = event.getDetails().getType();
    if (!LOGGABLE_DETAIL_TYPES.contains(eventType)) {
      log.debug("Telemetry not sent: type '{}' not configured ", eventType);
      return;
    }

    String applicationId = event.getDetails().getApplication();
    if (applicationId == null || applicationId.isEmpty()) {
      log.debug("Application ID must be non-null and not empty");
      return;
    }

    com.netflix.spinnaker.kork.proto.stats.Event loggedEvent =
        com.netflix.spinnaker.kork.proto.stats.Event.getDefaultInstance();

    for (TelemetryEventDataProvider dataProvider : dataProviders) {
      try {
        loggedEvent = dataProvider.populateData(event, loggedEvent);
      } catch (Exception e) {
        log.warn("Exception running {}", dataProvider.getClass().getSimpleName(), e);
      }
    }

    try {
      String jsonContent = JSON_PRINTER.print(loggedEvent);
      log.debug("Sending telemetry event:\n{}", jsonContent);

      registry
          .circuitBreaker(TELEMETRY_REGISTRY_NAME)
          .executeCallable(
              () ->
                  Retrofit2SyncCall.execute(
                      telemetryService.log(
                          RequestBody.create(jsonContent, MediaType.parse("application/json")))));
      log.debug("Telemetry sent!");
    } catch (CallNotPermittedException cnpe) {
      log.debug(
          "Telemetry not set: {} circuit breaker tripped - {}",
          TELEMETRY_REGISTRY_NAME,
          cnpe.getMessage());
    } catch (Exception e) {
      log.debug("Could not send Telemetry event {}", event, e);
    }
  }
}
