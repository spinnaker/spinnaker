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
import com.netflix.spinnaker.echo.artifacts.ArtifactExtractor
import com.netflix.spinnaker.echo.events.EventPropagator
import com.netflix.spinnaker.echo.scm.ScmWebhookHandler
import com.netflix.spinnaker.echo.scm.GitWebhookHandler
import com.netflix.spinnaker.echo.model.Event
import com.netflix.spinnaker.echo.model.Metadata
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
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

    if (type == 'git') {
      GitWebhookHandler handler
      try {
        handler = scmWebhookHandler.getHandler(source)
      } catch (Exception e) {
        log.error("Unable to handle SCM source: {}", source)
        throw e
      }
      handler.handle(event, postedEvent)
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

  private static class WebhookResponse {
    boolean eventProcessed;
    String eventId;
  }
}
