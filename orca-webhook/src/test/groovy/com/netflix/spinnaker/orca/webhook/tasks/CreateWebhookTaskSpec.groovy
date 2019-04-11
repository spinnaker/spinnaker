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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.webhook.service.WebhookService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpServerErrorException
import spock.lang.Specification
import spock.lang.Subject
import java.nio.charset.Charset

class CreateWebhookTaskSpec extends Specification {

  def pipeline = Execution.newPipeline("orca")

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
    result.context as Map == [
      deprecationWarning: "All webhook information will be moved beneath the key 'webhook', and the keys 'statusCode', 'buildInfo', 'statusEndpoint' and 'error' will be removed. Please migrate today.",
      statusCode: HttpStatus.OK,
      webhook: [
        statusCode: HttpStatus.OK,
        statusCodeValue: HttpStatus.OK.value()
      ]
    ]
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
    def bodyString = "Oh noes, you can't do this"

    createWebhookTask.webhookService = Stub(WebhookService) {
      exchange(
        HttpMethod.DELETE,
        "https://my-service.io/api/",
        [:],
        [:]
      ) >> new ResponseEntity<Map>([error: bodyString], HttpStatus.BAD_REQUEST)
    }

    when:
    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.TERMINAL
    result.context as Map == [
      deprecationWarning: "All webhook information will be moved beneath the key 'webhook', and the keys 'statusCode', 'buildInfo', 'statusEndpoint' and 'error' will be removed. Please migrate today.",
      statusCode: HttpStatus.BAD_REQUEST,
      buildInfo: [error: bodyString],
      webhook: [
        statusCode: HttpStatus.BAD_REQUEST,
        statusCodeValue: HttpStatus.BAD_REQUEST.value(),
        body: [error: bodyString],
        error: "The webhook request failed"
      ]
    ]
  }

  def "should retry on HTTP status 429"() {
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
      ) >> { throwHttpException(HttpStatus.TOO_MANY_REQUESTS, null) }
    }

    when:
    def result = createWebhookTask.execute(stage)

    then:
    def errorMessage = "error submitting webhook for pipeline ${stage.execution.id} to ${stage.context.url}, will retry."

    result.status == ExecutionStatus.RUNNING
    (result.context as Map) == [
      webhook: [
        statusCode: HttpStatus.TOO_MANY_REQUESTS,
        statusCodeValue: HttpStatus.TOO_MANY_REQUESTS.value(),
        error: errorMessage
      ]
    ]
  }

  def "should retry on name resolution failure"() {
    setup:
    def stage = new Stage(pipeline, "webhook", "My webhook", [:])

    createWebhookTask.webhookService = Stub(WebhookService) {
      exchange(_, _, _, _) >> {
        // throwing it like UserConfiguredUrlRestrictions::validateURI does
        throw new IllegalArgumentException("Invalid URL", new UnknownHostException("Temporary failure in name resolution"))
      }
    }

    when:
    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.RUNNING
    (result.context as Map) == [
      webhook: [
        error: "name resolution failure in webhook for pipeline ${stage.execution.id} to ${stage.context.url}, will retry."
      ]
    ]
  }

  def "should return TERMINAL on URL validation failure"() {
    setup:
    def stage = new Stage(pipeline, "webhook", "My webhook", [url: "wrong://my-service.io/api/"])

    createWebhookTask.webhookService = Stub(WebhookService) {
      exchange(_, _, _, _) >> {
        throw new IllegalArgumentException("Invalid URL")
      }
    }

    when:
    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.TERMINAL
    (result.context as Map) == [
      webhook: [
        error: "an exception occurred in webhook to wrong://my-service.io/api/: java.lang.IllegalArgumentException: Invalid URL"
      ]
    ]
  }

  def "should parse response correctly on failure"() {
    setup:
    def stage = new Stage(pipeline, "webhook", "My webhook", [
      url: "https://my-service.io/api/",
      method: "delete",
      payload: [:],
      customHeaders: [:]
    ])
    
    HttpStatus statusCode = HttpStatus.BAD_REQUEST

    createWebhookTask.webhookService = Stub(WebhookService) {
      exchange(
        HttpMethod.DELETE,
        "https://my-service.io/api/",
        [:],
        [:]
      ) >> { throwHttpException(statusCode, bodyString) }
    }

    when:
    def result = createWebhookTask.execute(stage)

    then:
    def errorMessage = "Error submitting webhook for pipeline ${stage.execution.id} to ${stage.context.url} with status code ${statusCode.value()}."

    result.status == ExecutionStatus.TERMINAL
    (result.context as Map) == [
      webhook: [
        statusCode: statusCode,
        statusCodeValue: statusCode.value(),
        body: body,
        error: errorMessage
      ]
    ]

    where:
    bodyString                                | body
    "true"                                    | "true"
    "123"                                     | "123"
    "{\"bad json\":"                          | "{\"bad json\":"
    "{\"key\" : {\"subkey\" : \"subval\"}}"   | [key: [subkey: "subval"]]
    "[\"1\", {\"k\": \"v\"}, 2]"              | ["1", [k: "v"], 2]
  }

  def "should return TERMINAL status if webhook returns one of fail fast HTTP status codes"() {
    setup:
    def stage = new Stage(pipeline, "webhook", "My webhook", [
      url: "https://my-service.io/api/",
      method: "get",
      payload: [:],
      customHeaders: [:],
      "failFastStatusCodes": [503]
    ])
    def bodyString = "Fail fast, ok?"

    createWebhookTask.webhookService = Stub(WebhookService) {
      exchange(
        HttpMethod.GET,
        "https://my-service.io/api/",
        [:],
        [:]
      ) >> { throwHttpException(HttpStatus.SERVICE_UNAVAILABLE, bodyString) }
    }

    when:
    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.TERMINAL
    result.context as Map == [
      webhook: [
        error: "Received a status code configured to fail fast, terminating stage.",
        statusCode: HttpStatus.SERVICE_UNAVAILABLE,
        statusCodeValue: HttpStatus.SERVICE_UNAVAILABLE.value(),
        body: bodyString
      ]
    ]
  }

  def "should throw on invalid payload"() {
    setup:
    def stage = new Stage(pipeline, "webhook", "My webhook", [
      url: "https://my-service.io/api/",
      method: "get",
      payload: [:],
      customHeaders: [:],
      "failFastStatusCodes": 503
    ])
    def bodyString = "Fail fast, ok?"

    createWebhookTask.webhookService = Stub(WebhookService) {
      exchange(
        HttpMethod.GET,
        "https://my-service.io/api/",
        [:],
        [:]
      ) >> { throwHttpException(HttpStatus.SERVICE_UNAVAILABLE, bodyString) }
    }

    when:
    createWebhookTask.execute(stage)

    then:
    thrown IllegalArgumentException
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
    result.context as Map == [
      deprecationWarning: "All webhook information will be moved beneath the key 'webhook', and the keys 'statusCode', 'buildInfo', 'statusEndpoint' and 'error' will be removed. Please migrate today.",
      statusCode: HttpStatus.CREATED,
      buildInfo: [success: true],
      webhook: [
        statusCode: HttpStatus.CREATED,
        statusCodeValue: HttpStatus.CREATED.value(),
        body: [success: true],
        statusEndpoint: "https://my-service.io/api/"
      ]
    ]
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
    result.context as Map == [
      deprecationWarning: "All webhook information will be moved beneath the key 'webhook', and the keys 'statusCode', 'buildInfo', 'statusEndpoint' and 'error' will be removed. Please migrate today.",
      statusCode: HttpStatus.CREATED,
      buildInfo: [success: true],
      webhook: [
        statusCode: HttpStatus.CREATED,
        statusCodeValue: HttpStatus.CREATED.value(),
        body: [success: true],
        statusEndpoint: "https://my-service.io/api/status/123"
      ]
    ]
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
    result.context as Map == [
      deprecationWarning: "All webhook information will be moved beneath the key 'webhook', and the keys 'statusCode', 'buildInfo', 'statusEndpoint' and 'error' will be removed. Please migrate today.",
      statusCode: HttpStatus.CREATED,
      buildInfo: body,
      webhook: [
        statusCode: HttpStatus.CREATED,
        statusCodeValue: HttpStatus.CREATED.value(),
        body: body,
        statusEndpoint: "https://my-service.io/api/status/123"
      ]
    ]
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
      webhook: [
        statusCode: HttpStatus.CREATED,
        statusCodeValue: HttpStatus.CREATED.value(),
        body: body,
        error: "The status URL couldn't be resolved, but 'Wait for completion' was checked",
        statusEndpoint: ["this", "is", "a", "list"]
      ]
    ]
    stage.context.statusEndpoint == null
  }

  // Tests https://github.com/spinnaker/spinnaker/issues/2163
  def "should evaluate statusUrlJsonPath for differing payloads"() {
    given:
    def stage = new Stage(pipeline, "webhook", "My webhook", [
      url: "https://my-service.io/api/",
      waitForCompletion: "true",
      statusUrlResolution: "webhookResponse",
      statusUrlJsonPath: 'concat("https://my-service.io/api/id/", $.id)'
    ])

    createWebhookTask.webhookService = Stub(WebhookService) {
      exchange(HttpMethod.POST, "https://my-service.io/api/", null, null) >>> [
        new ResponseEntity<Map>([ success: true, id: "1" ], HttpStatus.CREATED),
        new ResponseEntity<Map>([ success: true, id: "2" ], HttpStatus.CREATED)
      ]
    }

    when:
    def result1 = createWebhookTask.execute(stage)
    def result2 = createWebhookTask.execute(stage)

    then:
    result1.status == ExecutionStatus.SUCCEEDED
    result1.context.webhook.statusEndpoint == "https://my-service.io/api/id/1"

    result2.status == ExecutionStatus.SUCCEEDED
    result2.context.webhook.statusEndpoint == "https://my-service.io/api/id/2"
  }

  def "should support html in response"() {
    setup:
    def stage = new Stage(pipeline, "webhook", "My webhook", [
      url: "https://my-service.io/api/",
      method: "post",
      payload: [payload1: "Hello Spinnaker!"]
    ])

    createWebhookTask.webhookService = Stub(WebhookService) {
      exchange(
        HttpMethod.POST,
        "https://my-service.io/api/",
        [payload1: "Hello Spinnaker!"],
        null
      ) >> new ResponseEntity<String>("<html></html>", HttpStatus.OK)
    }

    when:
    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.context as Map == [
      deprecationWarning: "All webhook information will be moved beneath the key 'webhook', and the keys 'statusCode', 'buildInfo', 'statusEndpoint' and 'error' will be removed. Please migrate today.",
      statusCode: HttpStatus.OK,
      buildInfo: "<html></html>",
      webhook: [
        statusCode: HttpStatus.OK,
        statusCodeValue: HttpStatus.OK.value(),
        body: "<html></html>"
      ]
    ]
  }

// TODO: Remove test when removing the deprecated fields
  def "should add deprecation warning to the outputs"() {
    setup:
    def stage = new Stage(pipeline, "webhook", "My webhook", [
      url: "https://my-service.io/api/",
      method: "post",
      payload: [payload1: "Hello Spinnaker!"]
    ])

    createWebhookTask.webhookService = Stub(WebhookService) {
      exchange(
        HttpMethod.POST,
        "https://my-service.io/api/",
        [payload1: "Hello Spinnaker!"],
        null
      ) >> new ResponseEntity<Map>([:], HttpStatus.OK)
    }

    when:
    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.context as Map == [
      deprecationWarning: "All webhook information will be moved beneath the key 'webhook', and the keys 'statusCode', 'buildInfo', 'statusEndpoint' and 'error' will be removed. Please migrate today.",
      statusCode: HttpStatus.OK,
      webhook: [
        statusCode: HttpStatus.OK,
        statusCodeValue: HttpStatus.OK.value(),
      ]
    ]
  }

  private HttpServerErrorException throwHttpException(HttpStatus statusCode, String body) {
    if (body != null) {
      throw new HttpServerErrorException(
        statusCode,
        statusCode.name(),
        body.getBytes(Charset.defaultCharset()),
        Charset.defaultCharset())
    }

    throw new HttpServerErrorException(statusCode, statusCode.name())
  }
}
