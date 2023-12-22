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

package com.netflix.spinnaker.echo.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.echo.api.events.Event
import com.netflix.spinnaker.echo.api.events.Metadata
import com.netflix.spinnaker.echo.artifacts.ArtifactExtractor
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import com.netflix.spinnaker.echo.events.EventPropagator
import com.netflix.spinnaker.echo.scm.GitWebhookHandler
import com.netflix.spinnaker.echo.scm.ScmWebhookHandler
import groovy.util.logging.Slf4j
import io.cloudevents.CloudEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.util.CollectionUtils
import org.springframework.web.bind.annotation.*

@RestController
@Slf4j
class WebhooksController {

  @Autowired
  EventPropagator propagator

  @Autowired
  ObjectMapper mapper

  @Autowired
  ArtifactExtractor artifactExtractor

  @Autowired
  ScmWebhookHandler scmWebhookHandler

  @RequestMapping(value = '/webhooks/{type}/{source}', method = RequestMethod.POST)
  WebhooksController.WebhookResponse forwardEvent(@PathVariable String type,
                                                  @PathVariable String source,
                                                  @RequestBody String rawPayload,
                                                  @RequestHeader HttpHeaders headers) {
    Event event = new Event()
    boolean sendEvent = true
    event.details = new Metadata()
    event.details.source = source
    event.details.type = type
    event.details.requestHeaders = headers
    event.rawContent = rawPayload

    if (!rawPayload && source == 'bitbucket') {
      rawPayload = '{}'
    }

    Map postedEvent
    try {
      postedEvent = mapper.readValue(rawPayload, Map) ?: [:]
    } catch (Exception e) {
      log.error("Failed to parse payload: {}", rawPayload, e);
      throw e
    }
    event.content = postedEvent
    event.payload = new HashMap(postedEvent)
    if (headers.containsKey('X-Event-Key')) {
      event.content.event_type = headers['X-Event-Key'][0]
    }
    def filteredHeaders = CollectionUtils.toMultiValueMap(headers.findAll { headersPredicate(it) })

    if (type == 'git') {
      GitWebhookHandler handler
      try {
        handler = scmWebhookHandler.getHandler(source)
      } catch (Exception e) {
        log.error("Unable to handle SCM source: {}", source)
        throw e
      }
      handler.handle(event, postedEvent, new HttpHeaders(filteredHeaders))
      // shouldSendEvent should be called after the event
      // has been processed
      sendEvent = handler.shouldSendEvent(event)
    }

    if (!event.content.artifacts) {
      event.content.artifacts = artifactExtractor.extractArtifacts(type, source, event.payload)
    }

    log.info("Webhook ${type}:${source}:${event.content}")

    if (sendEvent) {
      propagator.processEvent(event)
    }

    return sendEvent ?
      WebhookResponse.newInstance(eventProcessed: true, eventId: event.eventId) :
      WebhookResponse.newInstance(eventProcessed: false);
  }

  // If your scm implementation needs access to headers, add them as a clause to this filter predicate
  private static boolean headersPredicate(Map.Entry<String, List<String>> header) {
    header.key.toLowerCase().startsWith("x-github")
  }

  @RequestMapping(value = '/webhooks/{type}', method = RequestMethod.POST)
  WebhooksController.WebhookResponse forwardEvent(@PathVariable String type,
                                                  @RequestBody Map postedEvent,
                                                  @RequestHeader HttpHeaders headers) {
    Event event = new Event()
    event.details = new Metadata()
    event.details.type = type
    event.details.requestHeaders = headers
    event.content = postedEvent

    if (event.content.source != null) {
      event.details.source = event.content.source;
    }

    log.info("Webhook ${type}:${event.details.source}:${event.content}")

    propagator.processEvent(event)

    WebhookResponse.newInstance(eventProcessed: true, eventId: event.eventId)
  }

  @RequestMapping(value = '/webhooks/cdevents/{source}', method = RequestMethod.POST)
  ResponseEntity<Void> forwardEvent(@PathVariable String source,
                                    @RequestBody CloudEvent cdevent,
                                    @RequestHeader HttpHeaders headers) {
    log.info("CDEvents Webhook received with source ${source} and with event type ${cdevent.getType()}")
    String ceDataJsonString  = headers.get("Ce-Data").get(0)
    log.info("CDEvent received with in the CloudEvent data request {}", ceDataJsonString)
    Map postedEvent
    try {
      postedEvent = mapper.readValue(ceDataJsonString, Map) ?: [:]
      if (postedEvent.get("customData") == null || !postedEvent.get("context") || !postedEvent.get("subject")) {
        throw new InvalidRequestException("Invalid CDEvent data posted with the CloudEvent RequestBody - " + postedEvent);
      }
    } catch (Exception e) {
      log.error("Failed to parse payload ceDataJsonString: {}", ceDataJsonString, e);
      throw e
    }

    Event event = new Event()
    event.details = new Metadata()
    event.details.source = source
    event.details.type = "cdevents"
    event.details.requestHeaders = headers
    event.rawContent = ceDataJsonString
    event.payload = new HashMap(postedEvent)
    event.content = new HashMap<>();

    Map customDataMap = new HashMap(postedEvent.get("customData"))
    def artifacts = customDataMap.get("artifacts")
    def parameters = customDataMap.get("parameters")
    if (artifacts){
      log.info("Artifacts received from postedEvent - {}", artifacts)
      event.content.artifacts = artifacts
    }
    if (parameters){
      log.info("Parameters received from postedEvent - {}", parameters)
      event.content.parameters = parameters
    }
    propagator.processEvent(event)

    WebhookResponse.newInstance(eventProcessed: true, eventId: event.eventId)
    ResponseEntity.ok().build();
  }

  private static class WebhookResponse {
    boolean eventProcessed;
    String eventId;
  }
}
