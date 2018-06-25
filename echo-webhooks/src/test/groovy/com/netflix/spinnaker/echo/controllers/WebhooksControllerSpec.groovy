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
import org.springframework.http.HttpHeaders
import spock.lang.Specification

class WebhooksControllerSpec extends Specification {

  void 'emits a transformed event for every webhook event'() {

    given:
    WebhooksController controller = new WebhooksController(mapper: new ObjectMapper())
    controller.propagator = Mock(EventPropagator)
    controller.artifactExtractor = Mock(ArtifactExtractor)
    controller.artifactExtractor.extractArtifacts(_, _, _) >> []

    when:
    controller.forwardEvent(
      'docker', 'ecr', '{"name": "something"}', new HttpHeaders()
    )

    then:
    1 * controller.propagator.processEvent(
      {
        it.details.type == 'docker' &&
          it.details.source == 'ecr' &&
          it.content.name == 'something'
      }
    )

  }

  void 'handles initial github ping'() {
    given:
    WebhooksController controller = new WebhooksController(mapper: new ObjectMapper())
    controller.propagator = Mock(EventPropagator)
    controller.artifactExtractor = Mock(ArtifactExtractor)
    controller.artifactExtractor.extractArtifacts(_, _, _) >> []

    when:
    controller.forwardEvent(
      'git',
      'github',
      '{"hook_id": 1337, "repository": {"full_name": "org/repo"}}',
      new HttpHeaders())

    then:
    0 * controller.propagator.processEvent(_)
  }

  void 'returns success status with eventId'() {
    def event

    given:
    WebhooksController controller = new WebhooksController(mapper: new ObjectMapper())
    controller.propagator = Mock(EventPropagator)
    controller.artifactExtractor = Mock(ArtifactExtractor)
    controller.artifactExtractor.extractArtifacts(_, _, _) >> []

    when:
    def response = controller.forwardEvent(
      'webhook',
      'test',
      '{}',
      new HttpHeaders())

    then:
    1 * controller.propagator.processEvent(_) >> {
      event = it[0]
    }

    response.eventProcessed == true
    response.eventId == event.eventId
  }

  void 'no source returns success status with eventId'() {
    def event

    given:
    WebhooksController controller = new WebhooksController(mapper: new ObjectMapper())
    controller.propagator = Mock(EventPropagator)
    controller.artifactExtractor = Mock(ArtifactExtractor)
    controller.artifactExtractor.extractArtifacts(_, _, _) >> []

    when:
    def response = controller.forwardEvent(
      'webhook',
      [:],
      new HttpHeaders())

    then:
    1 * controller.propagator.processEvent(_) >> {
      event = it[0]
    }

    response.eventProcessed == true
    response.eventId == event.eventId
  }
}
