/*
 * Copyright 2017 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.webhook.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.webhook.service.WebhookService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import spock.lang.Specification
import spock.lang.Subject

class CreateWebhookTaskSpec extends Specification {

  def pipeline = new Pipeline("orca")

  @Subject
  def createWebhookTask = new CreateWebhookTask()

  def "should create new webhook task with expected parameters"() {
    setup:
    def stage = new Stage(pipeline, "webhook", "My webhook", [
      url: "https://my-service.io/api/",
      method: "post",
      payload: [payload1: "Hello Spinnaker!"],
      customHeaders: [header1: "Header"]
    ])

    createWebhookTask.webhookService = Stub(WebhookService) {
      exchange(
        HttpMethod.POST,
        "https://my-service.io/api/",
        [payload1: "Hello Spinnaker!"],
        [header1: "Header"]
      ) >> new ResponseEntity<Map>([:], HttpStatus.OK)
    }

    when:
    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.context as Map == [statusCode: HttpStatus.OK]
  }

  def "should default to POST if no method is specified"() {
    setup:
    def stage = new Stage(pipeline, "webhook", [
      url: "https://my-service.io/api/"
    ])

    createWebhookTask.webhookService = Stub(WebhookService) {
      exchange(
        HttpMethod.POST,
        "https://my-service.io/api/",
        null,
        null
      ) >> new ResponseEntity<Map>([:], HttpStatus.OK)
    }

    when:
    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
  }

  def "should support GET"() {
    setup:
    def stage = new Stage(pipeline, "webhook", "My webhook", [
      url: "https://my-service.io/api/",
      waitForCompletion: "false",
      method: "get"
    ])

    createWebhookTask.webhookService = Stub(WebhookService) {
      exchange(
        HttpMethod.GET,
        "https://my-service.io/api/",
        null,
        null
      ) >> new ResponseEntity<Map>([:], HttpStatus.OK)
    }

    when:
    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
  }

  def "should return TERMINAL status if webhook is not returning 200 OK"() {
    setup:
    def stage = new Stage(pipeline, "webhook", "My webhook", [
      url: "https://my-service.io/api/",
      method: "delete",
      payload: [:],
      customHeaders: [:]
    ])

    createWebhookTask.webhookService = Stub(WebhookService) {
      exchange(
        HttpMethod.DELETE,
        "https://my-service.io/api/",
        [:],
        [:]
      ) >> new ResponseEntity<Map>([error: "Oh noes, you can't do this"], HttpStatus.BAD_REQUEST)
    }

    when:
    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.TERMINAL
    result.context as Map == [statusCode: HttpStatus.BAD_REQUEST, buildInfo: [error: "Oh noes, you can't do this"], error: "The request did not return a 2xx/3xx status"]
  }

  def "if statusUrlResolution is getMethod, should return SUCCEEDED status"() {
    setup:
    def stage = new Stage(pipeline, "webhook", "My webhook", [
      url: "https://my-service.io/api/",
      waitForCompletion: "true",
      statusUrlResolution: "getMethod"
    ])

    createWebhookTask.webhookService = Stub(WebhookService) {
      exchange(HttpMethod.POST, "https://my-service.io/api/", null, null) >> new ResponseEntity<Map>([success: true], HttpStatus.CREATED)
    }

    when:
    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.context as Map == [statusCode: HttpStatus.CREATED, buildInfo: [success: true], statusEndpoint: "https://my-service.io/api/"]
    stage.context.statusEndpoint == "https://my-service.io/api/"
  }

  def "if statusUrlResolution is locationHeader, should return SUCCEEDED status if the endpoint returns a Location header"() {
    setup:
    def stage = new Stage(pipeline, "webhook", "My webhook", [
      url: "https://my-service.io/api/",
      waitForCompletion: "true",
      statusUrlResolution: "locationHeader"
    ])

    createWebhookTask.webhookService = Stub(WebhookService) {
      def headers = new HttpHeaders()
      headers.add(HttpHeaders.LOCATION, "https://my-service.io/api/status/123")
      exchange(HttpMethod.POST, "https://my-service.io/api/", null, null) >> new ResponseEntity<Map>([success: true], headers, HttpStatus.CREATED)
    }

    when:
    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.context as Map == [statusCode: HttpStatus.CREATED, buildInfo: [success: true], statusEndpoint: "https://my-service.io/api/status/123"]
    stage.context.statusEndpoint == "https://my-service.io/api/status/123"
  }

  def "if statusUrlResolution is webhookResponse, should return SUCCEEDED status if the endpoint returns an URL in the body"() {
    setup:
    def stage = new Stage(pipeline, "webhook", "My webhook", [
      url: "https://my-service.io/api/",
      waitForCompletion: "true",
      statusUrlResolution: "webhookResponse",
      statusUrlJsonPath: '$.location'
    ])

    def body = [success: true, location: "https://my-service.io/api/status/123"]

    createWebhookTask.webhookService = Stub(WebhookService) {
      exchange(HttpMethod.POST, "https://my-service.io/api/", null, null) >> new ResponseEntity<Map>(body, HttpStatus.CREATED)
    }

    when:
    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.context as Map == [statusCode: HttpStatus.CREATED, buildInfo: body, statusEndpoint: "https://my-service.io/api/status/123"]
    stage.context.statusEndpoint == "https://my-service.io/api/status/123"
  }

  def "if statusUrlResolution is webhookResponse, should return TERMINAL if JSON path is wrong"() {
    setup:
    def stage = new Stage(pipeline, "webhook", "My webhook", [
      url: "https://my-service.io/api/",
      waitForCompletion: "true",
      statusUrlResolution: "webhookResponse",
      statusUrlJsonPath: '$.location.something'
    ])

    def body = [
      success: true,
      location: [
        url: "https://my-service.io/api/status/123",
        something: ["this", "is", "a", "list"]
      ]
    ]

    createWebhookTask.webhookService = Stub(WebhookService) {
      exchange(HttpMethod.POST, "https://my-service.io/api/", null, null) >> new ResponseEntity<Map>(body, HttpStatus.CREATED)
    }

    when:
    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.TERMINAL
    result.context as Map == [
      statusCode: HttpStatus.CREATED,
      buildInfo: body,
      error: "The status URL couldn't be resolved, but 'Wait for completion' was checked",
      statusUrlValue: ["this", "is", "a", "list"]
    ]
    stage.context.statusEndpoint == null
  }
}
