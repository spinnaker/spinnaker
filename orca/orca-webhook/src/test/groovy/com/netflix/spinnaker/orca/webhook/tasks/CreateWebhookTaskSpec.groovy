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
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.webhook.config.WebhookProperties
import com.netflix.spinnaker.orca.webhook.pipeline.WebhookStage
import com.netflix.spinnaker.orca.webhook.service.WebhookService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.nio.charset.Charset

class CreateWebhookTaskSpec extends Specification {

  def pipeline = PipelineExecutionImpl.newPipeline("orca")

  WebhookService webhookService = Mock()

  @Subject
  def createWebhookTask = new CreateWebhookTask(webhookService, new WebhookProperties(), new ObjectMapper())

  def "should create new webhook task with expected parameters"() {
    setup:
    def stage = new StageExecutionImpl(pipeline, "webhook", "My webhook", [
      url: "https://my-service.io/api/",
      method: "post",
      payload: [payload1: "Hello Spinnaker!"],
      customHeaders: [header1: "Header"]
    ])

    webhookService.callWebhook(stage) >> new ResponseEntity<Map>([:], HttpStatus.OK)

    when:
    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.context as Map == [
      webhook: new WebhookStage.WebhookResponseStageData(
        statusCode: HttpStatus.OK,
        statusCodeValue: HttpStatus.OK.value(),
        body: [:]
      )
    ]
  }

  def "should succeed when webhook call returns OK status"() {
    setup:
    def stage = new StageExecutionImpl(pipeline, "webhook", [
      url: "https://my-service.io/api/"
    ])

    webhookService.callWebhook(stage) >> new ResponseEntity<Map>([:], HttpStatus.OK)

    when:
    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
  }

  def "should return TERMINAL status if webhook is not returning 200 OK"() {
    setup:
    def stage = new StageExecutionImpl(pipeline, "webhook", "My webhook", [
      url: "https://my-service.io/api/",
      method: "delete",
      payload: [:],
      customHeaders: [:]
    ])
    def bodyString = "Oh noes, you can't do this"

    webhookService.callWebhook(stage) >> new ResponseEntity<Map>([error: bodyString], HttpStatus.BAD_REQUEST)

    when:
    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.TERMINAL
    result.context as Map == [
      webhook: new WebhookStage.WebhookResponseStageData(
        statusCode: HttpStatus.BAD_REQUEST,
        statusCodeValue: HttpStatus.BAD_REQUEST.value(),
        body: [error: bodyString],
        error: "The webhook request failed"
      )
    ]
  }

  @Unroll
  def "should retry on HTTP status #status"() {
    setup:
    def stage = new StageExecutionImpl(pipeline, "webhook", "My webhook", [
      url: "https://my-service.io/api/",
      method: "delete",
      payload: [:],
      customHeaders: [:],
      webhookRetryStatusCodes: [404, 401]
    ])

    webhookService.callWebhook(stage) >> { throwHttpException(status, null) }
    createWebhookTask.webhookProperties.defaultRetryStatusCodes = [429,403]

    when:
    def result = createWebhookTask.execute(stage)

    then:
    def errorMessage = "Error submitting webhook for pipeline ${stage.execution.id} to ${stage.context.url} with status code ${status.value()}, will retry."

    result.status == ExecutionStatus.RUNNING
    (result.context as Map) == [
      webhook: new WebhookStage.WebhookResponseStageData(
        statusCode: status,
        statusCodeValue: status.value(),
        error: errorMessage
      )
    ]

    where:
    status << [HttpStatus.TOO_MANY_REQUESTS, HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND, HttpStatus.UNAUTHORIZED]
  }

  def "should retry on name resolution failure"() {
    setup:
    def stage = new StageExecutionImpl(pipeline, "webhook", "My webhook", [url: "https://my-service.io/api/"])

    webhookService.callWebhook(stage) >> {
        // throwing it like UserConfiguredUrlRestrictions::validateURI does
        throw new Exception("Invalid URL", new UnknownHostException("Temporary failure in name resolution"))
    }

    when:
    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.RUNNING
    (result.context as Map) == [
      webhook: new WebhookStage.WebhookResponseStageData(
        error: "Remote host resolution failure in webhook for pipeline ${stage.execution.id} to ${stage.context.url}, will retry."
      )
    ]
  }

  def "should retry on timeout for GET request"() {
    setup:
    def webhookUrl = "https://my-service.io/api/"
    def stage = new StageExecutionImpl(pipeline, "webhook", "My webhook", [
      url: webhookUrl,
      method: "get",
    ])

    webhookService.callWebhook(stage) >> {
        throw new ResourceAccessException("I/O error on GET request for ${webhookUrl}", new SocketTimeoutException("timeout"))
    }

    when:
    def result = createWebhookTask.execute(stage)

    then:
    def errorMessage = "Socket timeout in webhook on GET request for pipeline ${stage.execution.id} to ${stage.context.url}, will retry."
    result.status == ExecutionStatus.RUNNING
    (result.context as Map) == [
      webhook: new WebhookStage.WebhookResponseStageData(
        error: errorMessage
      )
    ]
  }

  def "should return TERMINAL on URL validation failure"() {
    setup:
    def stage = new StageExecutionImpl(pipeline, "webhook", "My webhook", [url: "wrong://my-service.io/api/"])

    webhookService.callWebhook(stage) >> {
        throw new IllegalArgumentException("Invalid URL")
    }

    when:
    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.TERMINAL
    (result.context as Map) == [
      webhook: new WebhookStage.WebhookResponseStageData(
        error: "An exception occurred for pipeline ${stage.execution.id} performing a request to wrong://my-service.io/api/. java.lang.IllegalArgumentException: Invalid URL"
      )
    ]
  }

  def "should parse response correctly on failure"() {
    setup:
    def stage = new StageExecutionImpl(pipeline, "webhook", "My webhook", [
      url: "https://my-service.io/api/",
      method: "delete",
      payload: [:],
      customHeaders: [:]
    ])

    HttpStatus statusCode = HttpStatus.BAD_REQUEST

    webhookService.callWebhook(stage) >> { throwHttpException(statusCode, bodyString) }

    when:
    def result = createWebhookTask.execute(stage)

    then:
    def errorMessage = "Error submitting webhook for pipeline ${stage.execution.id} to ${stage.context.url} with status code ${statusCode.value()}."

    result.status == ExecutionStatus.TERMINAL
    (result.context as Map) == [
      webhook: new WebhookStage.WebhookResponseStageData(
        statusCode: statusCode,
        statusCodeValue: statusCode.value(),
        body: body,
        error: errorMessage
      )
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
    def stage = new StageExecutionImpl(pipeline, "webhook", "My webhook", [
      url: "https://my-service.io/api/",
      method: "get",
      payload: [:],
      customHeaders: [:],
      "failFastStatusCodes": [503]
    ])
    def bodyString = "Fail fast, ok?"

    webhookService.callWebhook(stage) >> { throwHttpException(HttpStatus.SERVICE_UNAVAILABLE, bodyString) }

    when:
    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.TERMINAL
    result.context as Map == [
      webhook: new WebhookStage.WebhookResponseStageData(
        error: "Received status code 503, which is configured to fail fast, terminating stage for pipeline ${stage.getExecution().id} to ${stage.context.url}",
        statusCode: HttpStatus.SERVICE_UNAVAILABLE,
        statusCodeValue: HttpStatus.SERVICE_UNAVAILABLE.value(),
        body: bodyString
      )
    ]
  }

  def "should throw on invalid payload"() {
    setup:
    def stage = new StageExecutionImpl(pipeline, "webhook", "My webhook", [
      url: "https://my-service.io/api/",
      method: "get",
      payload: [:],
      customHeaders: [:],
      "failFastStatusCodes": 503
    ])
    def bodyString = "Fail fast, ok?"

    webhookService.callWebhook(stage) >> { throwHttpException(HttpStatus.SERVICE_UNAVAILABLE, bodyString) }

    when:
    createWebhookTask.execute(stage)

    then:
    thrown IllegalArgumentException
  }

  def "if statusUrlResolution is getMethod, should return SUCCEEDED status"() {
    setup:
    def stage = new StageExecutionImpl(pipeline, "webhook", "My webhook", [
      url: "https://my-service.io/api/",
      waitForCompletion: "true",
      statusUrlResolution: "getMethod"
    ])

    webhookService.callWebhook(stage) >> new ResponseEntity<Map>([success: true], HttpStatus.CREATED)

    when:
    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.context as Map == [
      webhook: new WebhookStage.WebhookResponseStageData(
        statusCode: HttpStatus.CREATED,
        statusCodeValue: HttpStatus.CREATED.value(),
        body: [success: true],
        statusEndpoint: "https://my-service.io/api/"
      )
    ]
    stage.context.statusEndpoint == "https://my-service.io/api/"
  }

  def "if statusUrlResolution is locationHeader, should return SUCCEEDED status if the endpoint returns a Location header"() {
    setup:
    def stage = new StageExecutionImpl(pipeline, "webhook", "My webhook", [
      url: "https://my-service.io/api/",
      waitForCompletion: "true",
      statusUrlResolution: "locationHeader"
    ])

    def headers = new HttpHeaders()
    headers.add(HttpHeaders.LOCATION, "https://my-service.io/api/status/123")
    webhookService.callWebhook(stage) >> new ResponseEntity<Map>([success: true], headers, HttpStatus.CREATED)

    when:
    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.context as Map == [
      webhook: new WebhookStage.WebhookResponseStageData(
        headers:[Location: "https://my-service.io/api/status/123"],
        statusCode: HttpStatus.CREATED,
        statusCodeValue: HttpStatus.CREATED.value(),
        body: [success: true],
        statusEndpoint: "https://my-service.io/api/status/123"
      )
    ]
    stage.context.statusEndpoint == "https://my-service.io/api/status/123"
  }

  def "if statusUrlResolution is webhookResponse, should return SUCCEEDED status if the endpoint returns an URL in the body"() {
    setup:
    def stage = new StageExecutionImpl(pipeline, "webhook", "My webhook", [
      url: "https://my-service.io/api/",
      waitForCompletion: "true",
      statusUrlResolution: "webhookResponse",
      statusUrlJsonPath: '$.location'
    ])

    def body = [success: true, location: "https://my-service.io/api/status/123"]

    webhookService.callWebhook(stage) >> new ResponseEntity<Map>(body, HttpStatus.CREATED)

    when:
    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.context as Map == [
      webhook: new WebhookStage.WebhookResponseStageData(
        statusCode: HttpStatus.CREATED,
        statusCodeValue: HttpStatus.CREATED.value(),
        body: body,
        statusEndpoint: "https://my-service.io/api/status/123"
      )
    ]
    stage.context.statusEndpoint == "https://my-service.io/api/status/123"
  }

  def "if statusUrlResolution is webhookResponse, should return TERMINAL if JSON path is wrong"() {
    setup:
    def stage = new StageExecutionImpl(pipeline, "webhook", "My webhook", [
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

    webhookService.callWebhook(stage) >> new ResponseEntity<Map>(body, HttpStatus.CREATED)

    when:
    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.TERMINAL
    result.context['webhook']['statusCode'] == HttpStatus.CREATED
    result.context['webhook']['statusCodeValue'] == HttpStatus.CREATED.value()
    result.context['webhook']['body'] == body
    result.context['webhook']['error'].toString().contains('Exception while resolving status check URL')
    stage.context.statusEndpoint == null
  }

  // Tests https://github.com/spinnaker/spinnaker/issues/2163
  def "should evaluate statusUrlJsonPath for differing payloads"() {
    given:
    def stage = new StageExecutionImpl(pipeline, "webhook", "My webhook", [
      url: "https://my-service.io/api/",
      waitForCompletion: "true",
      statusUrlResolution: "webhookResponse",
      statusUrlJsonPath: 'concat("https://my-service.io/api/id/", $.id)'
    ])

    webhookService.callWebhook(stage) >>> [
        new ResponseEntity<Map>([ success: true, id: "1" ], HttpStatus.CREATED),
        new ResponseEntity<Map>([ success: true, id: "2" ], HttpStatus.CREATED)
    ]

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
    def stage = new StageExecutionImpl(pipeline, "webhook", "My webhook", [
      url: "https://my-service.io/api/",
      method: "post",
      payload: [payload1: "Hello Spinnaker!"]
    ])

    webhookService.callWebhook(stage) >> new ResponseEntity<String>("<html></html>", HttpStatus.OK)

    when:
    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.context as Map == [
      webhook: new WebhookStage.WebhookResponseStageData(
        statusCode: HttpStatus.OK,
        statusCodeValue: HttpStatus.OK.value(),
        body: "<html></html>"
      )
    ]
  }

  def "should inject artifacts from response"() {
    setup:
    def stage = new StageExecutionImpl(pipeline, "webhook", "My webhook", [
      url: "https://my-service.io/api/",
      method: "post",
      payload: [payload1: "Hello Spinnaker!"],
      expectedArtifacts: [[matchArtifact: [ name: "overrides", type: "github/file" ]]]
    ])

    webhookService.callWebhook(stage) >> new ResponseEntity<Map>([
        artifacts: [[ name: "overrides", type: "github/file", artifactAccount: "github", reference: "https://api.github.com/file", version: "master" ]]
      ], HttpStatus.OK)

    when:
    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.context as Map == [
      webhook: new WebhookStage.WebhookResponseStageData(
        statusCode: HttpStatus.OK,
        statusCodeValue: HttpStatus.OK.value(),
        body: [ artifacts: [[ name: "overrides", type: "github/file", artifactAccount: "github", reference: "https://api.github.com/file", version: "master" ]]]
      ),
      artifacts: [[ name: "overrides", type: "github/file", artifactAccount: "github", reference: "https://api.github.com/file", version: "master" ]]
    ]
  }

  @Unroll
  def 'should honor the web hook #webHookUrl protocol/scheme for status check URL #responseStatusCheckUrl'() {
    setup:
    Map<String, Object> stageContext = [method: HttpMethod.POST,
                                        payload: ['test': 'test'],
                                        waitForCompletion: true,
                                        statusUrlJsonPath:  '$.statusCheckUrl']
    def stage = new StageExecutionImpl(pipeline, "webhook", "Protocol scheme webhook", stageContext)

    when:
    stage.context.statusUrlResolution = statusUrlResolution
    stage.context.url = webHookUrl

    def headers = new HttpHeaders()
    if (statusUrlResolution == WebhookProperties.StatusUrlResolution.locationHeader) {
      headers.add(HttpHeaders.LOCATION, responseStatusCheckUrl)
    }
    headers.add(HttpHeaders.CONTENT_TYPE, "application/json")

    webhookService.callWebhook(stage) >> new ResponseEntity<Map>(['statusCheckUrl': responseStatusCheckUrl] as Map, headers, HttpStatus.OK)

    def result = createWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    stage.context.statusEndpoint == resultstatusCheckUrl
    result.context.webhook.headers.size() >= 1

    where:
    webHookUrl                   | statusUrlResolution                                    | responseStatusCheckUrl             || resultstatusCheckUrl
    'proto-abc://test.com'       | WebhookProperties.StatusUrlResolution.getMethod        | 'https://test.com'                 || 'proto-abc://test.com'
    'proto-abc://test.com'       | WebhookProperties.StatusUrlResolution.webhookResponse  | 'https://test.com'                 || 'proto-abc://test.com'
    'proto-abc://test.com'       | WebhookProperties.StatusUrlResolution.locationHeader   | 'https://test.com/api/status/123'  || 'proto-abc://test.com/api/status/123'
    'proto-abc://test.com'       | WebhookProperties.StatusUrlResolution.getMethod        | 'https://blah.com'                 || 'proto-abc://test.com'
    'proto-abc://test.com'       | WebhookProperties.StatusUrlResolution.webhookResponse  | 'https://blah.com'                 || 'https://blah.com'
    'proto-abc://test.com'       | WebhookProperties.StatusUrlResolution.locationHeader   | 'https://blah.com/api/status/123'  || 'https://blah.com/api/status/123'
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
