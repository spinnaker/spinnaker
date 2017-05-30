/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.echo.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.echo.config.RestUrls
import com.netflix.spinnaker.echo.model.Event
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Event listener for echo events
 */
@Component
@Slf4j
@ConditionalOnProperty('rest.enabled')
@SuppressWarnings('GStringExpressionWithinString')
class RestEventListener implements EchoEventListener {

  ObjectMapper mapper = new ObjectMapper()

  @Autowired
  RestUrls restUrls

  @Autowired
  RestEventTemplateEngine restEventTemplateEngine

  @Value('${rest.defaultEventName:spinnaker_events}')
  String eventName

  @Value('${rest.defaultFieldName:payload}')
  String fieldName

  @Override
  void processEvent(Event event) {
    restUrls.services.each { service ->
      try {
        Map eventAsMap = mapper.convertValue(event, Map)
        Map sentEvent = eventAsMap

        if (service.config.flatten) {
          eventAsMap.content = mapper.writeValueAsString(eventAsMap.content)
          eventAsMap.details = mapper.writeValueAsString(eventAsMap.details)
        }

        if (service.config.wrap) {
          if (service.config.template) {
            sentEvent = restEventTemplateEngine.render(service.config.template as String, sentEvent)
          } else {
            sentEvent = [
              eventName: "${service.config.eventName ?: eventName}" as String,
            ]
            sentEvent["${service.config.fieldName ?: fieldName}" as String] = eventAsMap
          }
        }
        service.client.recordEvent(sentEvent)
      } catch (e) {
        log.error("Could not send event ${event} to ${service.config.url}", e)
      }
    }
  }
}
