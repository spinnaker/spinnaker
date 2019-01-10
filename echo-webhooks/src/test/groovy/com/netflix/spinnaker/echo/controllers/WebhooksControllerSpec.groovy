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

  void 'handles Bitbucket Server ping'() {
    def event

    given:
    WebhooksController controller = new WebhooksController(mapper: new ObjectMapper())
    controller.propagator = Mock(EventPropagator)
    controller.artifactExtractor = Mock(ArtifactExtractor)
    controller.artifactExtractor.extractArtifacts(_, _, _) >> []

    when:
    def response = controller.forwardEvent(
      'git',
      'bitbucket',
      '',
      new HttpHeaders())

    then:
    0 * controller.propagator.processEvent(_)

    response.eventProcessed == false
  }

  void 'handles Bitbucket Server Push Webhook'() {
    def event

    given:
    WebhooksController controller = new WebhooksController(mapper: new ObjectMapper())
    controller.propagator = Mock(EventPropagator)
    controller.artifactExtractor = Mock(ArtifactExtractor)
    controller.artifactExtractor.extractArtifacts(_, _, _) >> []

    when:
    def response = controller.forwardEvent(
      'git',
      'bitbucket',
      '''{
  "eventKey":"repo:refs_changed",
  "date":"2017-09-19T09:45:32+1000",
  "actor":{
    "name":"admin",
    "emailAddress":"admin@example.com",
    "id":1,
    "displayName":"Administrator",
    "active":true,
    "slug":"admin",
    "type":"NORMAL"
  },
  "repository":{
    "slug":"repository",
    "id":84,
    "name":"repository",
    "scmId":"git",
    "state":"AVAILABLE",
    "statusMessage":"Available",
    "forkable":true,
    "project":{
      "key":"PROJ",
      "id":84,
      "name":"project",
      "public":false,
      "type":"NORMAL"
    },
    "public":false
  },
  "changes":[
    {
      "ref":{
        "id":"refs/heads/master",
        "displayId":"master",
        "type":"BRANCH"
      },
      "refId":"refs/heads/master",
      "fromHash":"ecddabb624f6f5ba43816f5926e580a5f680a932",
      "toHash":"178864a7d521b6f5e720b386b2c2b0ef8563e0dc",
      "type":"UPDATE"
    }
  ]
}''',
      new HttpHeaders())

    then:
    1 * controller.propagator.processEvent(_) >> {
      event = it[0]
    }

    response.eventProcessed == true
    response.eventId == event.eventId
  }

  void 'handles Bitbucket Server Merge Webhook'() {
    def event
    given:
    WebhooksController controller = new WebhooksController(mapper: new ObjectMapper())
    controller.propagator = Mock(EventPropagator)
    controller.artifactExtractor = Mock(ArtifactExtractor)
    controller.artifactExtractor.extractArtifacts(_, _, _) >> []

    when:
    def response = controller.forwardEvent(
      'git',
      'bitbucket',
      '''{
  "eventKey":"pr:merged",
  "date":"2017-09-19T10:39:36+1000",
  "actor":{
    "name":"user",
    "emailAddress":"user@example.com",
    "id":2,
    "displayName":"User",
    "active":true,
    "slug":"user",
    "type":"NORMAL"
  },
  "pullRequest":{
    "id":9,
    "version":2,
    "title":"file edited online with Bitbucket",
    "state":"MERGED",
    "open":false,
    "closed":true,
    "createdDate":1505781560908,
    "updatedDate":1505781576361,
    "closedDate":1505781576361,
    "fromRef":{
      "id":"refs/heads/admin/file-1505781548644",
      "displayId":"admin/file-1505781548644",
      "latestCommit":"45f9690c928915a5e1c4366d5ee1985eea03f05d",
      "repository":{
        "slug":"repository",
        "id":84,
        "name":"repository",
        "scmId":"git",
        "state":"AVAILABLE",
        "statusMessage":"Available",
        "forkable":true,
        "project":{
          "key":"PROJ",
          "id":84,
          "name":"project",
          "public":false,
          "type":"NORMAL"
        },
        "public":false
      }
    },
    "toRef":{
      "id":"refs/heads/master",
      "displayId":"master",
      "latestCommit":"8d2ad38c918fa6943859fca2176c89ea98b92a21",
      "repository":{
        "slug":"repository",
        "id":84,
        "name":"repository",
        "scmId":"git",
        "state":"AVAILABLE",
        "statusMessage":"Available",
        "forkable":true,
        "project":{
          "key":"PROJ",
          "id":84,
          "name":"project",
          "public":false,
          "type":"NORMAL"
        },
        "public":false
      }
    },
    "locked":false,
    "author":{
      "user":{
        "name":"admin",
        "emailAddress":"admin@example.com",
        "id":1,
        "displayName":"Administrator",
        "active":true,
        "slug":"admin",
        "type":"NORMAL"
      },
      "role":"AUTHOR",
      "approved":false,
      "status":"UNAPPROVED"
    },
    "reviewers":[

    ],
    "participants":[
      {
        "user":{
          "name":"user",
          "emailAddress":"user@example.com",
          "id":2,
          "displayName":"User",
          "active":true,
          "slug":"user",
          "type":"NORMAL"
        },
        "role":"PARTICIPANT",
        "approved":false,
        "status":"UNAPPROVED"
      }
    ],
    "properties":{
      "mergeCommit":{
        "displayId":"7e48f426f0a",
        "id":"7e48f426f0a6e47c5b5e862c31be6ca965f82c9c"
      }
    }
  }
}''',
    new HttpHeaders())

    then:
    1 * controller.propagator.processEvent(_) >> {
      event = it[0]
    }

    response.eventProcessed == true
    response.eventId == event.eventId
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
