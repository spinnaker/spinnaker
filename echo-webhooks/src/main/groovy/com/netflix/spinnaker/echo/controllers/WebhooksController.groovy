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

import com.netflix.spinnaker.echo.events.EventPropagator
import com.netflix.spinnaker.echo.model.Event
import com.netflix.spinnaker.echo.model.Metadata
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@RestController
@Slf4j
class WebhooksController {

  @Autowired
  EventPropagator propagator

  @RequestMapping(value = '/webhooks/{type}/{source}', method = RequestMethod.POST)
  void forwardEvent(@PathVariable String type, @PathVariable String source, @RequestBody Map postedEvent) {
    Event event = new Event()
    boolean sendEvent = true
    event.details = new Metadata()
    event.details.source = source
    event.details.type = type
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
      }
      if (source == 'github') {
        if (event.content.hook_id) {
          log.info("Webook ping received from github hook_id=${event.content.hook_id} repository=${event.content.repository.full_name}")
          sendEvent = false
        } else {
          event.content.hash = postedEvent.after
          event.content.branch = postedEvent.ref.replace('refs/heads/', '')
          event.content.repoProject = postedEvent.repository.owner.name
          event.content.slug = postedEvent.repository.name
        }
      }
    }

    log.info("Webhook ${type}:${source}:${event.content}")

    if (sendEvent) {
      propagator.processEvent(event)
    }
  }

  @RequestMapping(value = '/webhooks/{type}', method = RequestMethod.POST)
  void forwardEvent(@PathVariable String type, @RequestBody Map postedEvent) {
    Event event = new Event()
    event.details = new Metadata()
    event.details.type = type
    event.content = postedEvent

    if (event.content.source != null) {
      event.details.source = event.content.source;
    }

    log.info("Webhook ${type}:${event.details.source}:${event.content}")

    propagator.processEvent(event)
  }
}
