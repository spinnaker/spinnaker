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
import com.netflix.spinnaker.echo.artifacts.MessageArtifactTranslator
import com.netflix.spinnaker.echo.config.WebhookProperties
import com.netflix.spinnaker.echo.events.EventPropagator
import com.netflix.spinnaker.echo.model.Event
import com.netflix.spinnaker.echo.model.Metadata
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

import static net.logstash.logback.argument.StructuredArguments.kv

@RestController
@Slf4j
class WebhooksController {

  @Autowired
  EventPropagator propagator

  @Autowired
  ObjectMapper mapper

  @Autowired(required = false)
  WebhookProperties webhookProperties

  @RequestMapping(value = '/webhooks/{type}/{source}', method = RequestMethod.POST)
  void forwardEvent(@PathVariable String type,
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

    Map postedEvent = mapper.readValue(rawPayload, Map)
    event.content = postedEvent

    if (type == 'git') {
      if (source == 'stash') {
        event.content.hash = postedEvent.refChanges?.first().toHash
        event.content.branch = postedEvent.refChanges?.first().refId.replace('refs/heads/', '')
        event.content.repoProject = postedEvent.repository.project.key
        event.content.slug = postedEvent.repository.slug
        if (event.content.hash.toString().startsWith('000000000')) {
          sendEvent = false
        }
      } else if (source == 'github') {
        if (event.content.hook_id) {
          log.info('Webhook ping received from github {} {} {}', kv('hook_id', event.content.hook_id), kv('repository', event.content.repository.full_name))
          sendEvent = false
        } else {
          event.content.hash = postedEvent.after
          event.content.branch = postedEvent.ref.replace('refs/heads/', '')
          event.content.repoProject = postedEvent.repository.owner.name
          event.content.slug = postedEvent.repository.name
        }
      } else if (source == 'bitbucket') {

        if (headers.containsKey('X-Event-Key')) {
          event.content.event_type = headers['X-Event-Key'][0]
        }

        if (event.content.event_type == "repo:push" && event.content.push) {
          event.content.hash = postedEvent.push.changes?.first().commits?.first().hash
          event.content.branch = postedEvent.push.changes?.first().new.name
        } else if (event.content.event_type == "pullrequest:fulfilled" && event.content.pullrequest) {
          event.content.hash = postedEvent.pullrequest.merge_commit?.hash
          event.content.branch = postedEvent.pullrequest.destination?.branch?.name
        }
        event.content.repoProject = postedEvent.repository.owner.username
        event.content.slug = postedEvent.repository.full_name.tokenize('/')[1]
        if (event.content.hash.toString().startsWith('000000000')) {
          sendEvent = false
        }
        log.info('Webhook event received {} {} {} {} {} {}', kv('type', type), kv('event_type', event.content.event_type), kv('hook_id', event.content.hook_id), kv('repository', event.content.repository.full_name), kv('request_id', event.content.request_id), kv('branch', event.content.branch))
      }
    } else if (webhookProperties && (type == 'artifact' || type == 'artifacts')) {
      String templatePath = webhookProperties.getTemplatePathForSource(source)
      // empty template path is the identity translator;
      MessageArtifactTranslator translator = new MessageArtifactTranslator(templatePath)
      try {
        event.content.artifacts = translator.parseArtifacts(event.rawContent)
        log.info("Webhook artifacts were processed: {}", event.content.artifacts);
      } catch (Exception e) {
        log.error("Failed to translate artifacts (ignoring webhook): {}", e.getMessage(), e)
        sendEvent = false
      }
    }

    log.info("Webhook ${type}:${source}:${event.content}")

    if (sendEvent) {
      propagator.processEvent(event)
    }
  }

  @RequestMapping(value = '/webhooks/{type}', method = RequestMethod.POST)
  void forwardEvent(@PathVariable String type,
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
  }
}
