/*
 * Copyright 2019 Netflix, Inc.
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
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.config.RestUrls;
import com.netflix.spinnaker.echo.extension.rest.RestEventParser;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.kork.annotations.Alpha;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Event listener for echo events */
@Component
@ConditionalOnProperty("rest.enabled")
class RestEventListener implements EchoEventListener {

  private static final Logger log = LoggerFactory.getLogger(RestEventListener.class);

  private ObjectMapper mapper = new ObjectMapper();

  private RestUrls restUrls;
  private RestEventTemplateEngine restEventTemplateEngine;
  private Registry registry;

  @Alpha private Optional<RestEventParser> restEventParser;

  @Value("${rest.default-event-name:spinnaker_events}")
  private String eventName;

  @Value("${rest.default-field-name:payload}")
  private String fieldName;

  @Autowired
  RestEventListener(
      RestUrls restUrls,
      RestEventTemplateEngine restEventTemplateEngine,
      Optional<RestEventParser> restEventParser,
      Registry registry) {
    this.restUrls = restUrls;
    this.restEventTemplateEngine = restEventTemplateEngine;
    this.restEventParser = restEventParser;
    this.registry = registry;
  }

  @Override
  public void processEvent(Event event) {
    restUrls
        .getServices()
        .forEach(
            (service) -> {
              try {
                Map<String, Object> eventMap = mapper.convertValue(event, Map.class);

                if (service.getConfig().getFlatten() && restEventParser.isPresent()) {
                  RestEventParser.FlatEvent flatEvent =
                      restEventParser
                          .get()
                          .flattenEvent(mapper.convertValue(event, RestEventParser.Event.class));
                  eventMap.put("content", flatEvent.getContent());
                  eventMap.put("details", flatEvent.getDetails());
                } else if (service.getConfig().getFlatten()) {
                  eventMap.put("content", mapper.writeValueAsString(eventMap.get("content")));
                  eventMap.put("details", mapper.writeValueAsString(eventMap.get("details")));
                } else if (restEventParser.isPresent()) {
                  eventMap =
                      restEventParser
                          .get()
                          .parseEvent(mapper.convertValue(event, RestEventParser.Event.class));
                }

                if (service.getConfig().getWrap()) {
                  if (service.getConfig().getTemplate() != null) {
                    eventMap =
                        restEventTemplateEngine.render(service.getConfig().getTemplate(), eventMap);
                  } else {
                    Map<String, Object> m = new HashMap<>();

                    m.put(
                        "eventName",
                        service.getConfig().getEventName() == null
                            ? eventName
                            : service.getConfig().getEventName());

                    m.put(
                        service.getConfig().getFieldName() == null
                            ? fieldName
                            : service.getConfig().getFieldName(),
                        eventMap);

                    eventMap = m;
                  }
                }
                service.getClient().recordEvent(eventMap);
              } catch (Exception e) {
                if (event != null
                    && event.getDetails() != null
                    && event.getDetails().getSource() != null
                    && event.getDetails().getType() != null) {
                  log.error(
                      "Could not send event source={}, type={} to {}.\n Event details: {}",
                      event.getDetails().getSource(),
                      event.getDetails().getType(),
                      service.getConfig().getUrl(),
                      event,
                      e);
                } else {
                  log.error("Could not send event.", e);
                }

                registry
                    .counter("event.send.errors", "exception", e.getClass().getName())
                    .increment();
              }
            });
  }
}
