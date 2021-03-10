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

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.webhook.config.WebhookProperties
import com.netflix.spinnaker.orca.webhook.pipeline.WebhookStage
import com.netflix.spinnaker.orca.webhook.service.WebhookService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class MonitorWebhookTaskSpec extends Specification {

  def pipeline = PipelineExecutionImpl.newPipeline("orca")

  WebhookService webhookService = Mock()

  @Subject
  MonitorWebhookTask monitorWebhookTask = new MonitorWebhookTask(webhookService, new WebhookProperties())

  @Unroll
  def "should fail if required parameter(url: #url, statusUrl: #statusEndpoint) is missing"() {
    setup:
    def stage = new StageExecutionImpl(pipeline, "webhook", [
        statusEndpoint: statusEndpoint,
        url: webhookurl,
        monitorOnly: monitorOnly,
        statusJsonPath: '$.status',
        successStatuses: 'SUCCESS',
        canceledStatuses: 'CANCELED',
        terminalStatuses: 'TERMINAL',
        someVariableWeDontCareAbout: 'Hello!'
    ])

    when:
    monitorWebhookTask.execute stage

    then:
    def ex = thrown IllegalStateException
    ex.message.startsWith("Missing required parameter")

    where:
    statusEndpoint | webhookurl        | monitorOnly
    null           | null              | true
    null           | 'http://test.net' | false
  }

  def "should fail if no parameters are supplied"() {
    setup:
    def stage = new StageExecutionImpl(pipeline, "webhook", [:])

    when:
    monitorWebhookTask.execute stage

    then:
    def ex = thrown IllegalStateException
    ex.message == "Missing required parameter: statusEndpoint = null" as String
  }

  def "should fail in case of URL validation error"() {
    setup:
    def stage = new StageExecutionImpl(pipeline, "webhook", [
        statusEndpoint: 'https://my-service.io/api/status/123',
        statusJsonPath: '$.status'])

    webhookService.getWebhookStatus(_) >> { throw new IllegalArgumentException("Invalid URL") }

    when:
    def result = monitorWebhookTask.execute(stage)

    then:
    result.status == ExecutionStatus.TERMINAL
    result.context.webhook.monitor.error == "an exception occurred in webhook monitor to https://my-service.io/api/status/123: java.lang.IllegalArgumentException: Invalid URL"
  }

  def "should retry in case of name resolution error"() {
    setup:
    def stage = new StageExecutionImpl(pipeline, "webhook", [
        statusEndpoint: 'https://my-service.io/api/status/123',
        statusJsonPath: '$.status'])

    webhookService.getWebhookStatus(_) >> { throw new Exception("Invalid URL", new UnknownHostException()) }

    when:
    def result = monitorWebhookTask.execute stage

    then:
    result.status == ExecutionStatus.RUNNING
  }

  def "should retry in case of timeout"() {
    setup:
    def stage = new StageExecutionImpl(pipeline, "webhook", [
        statusEndpoint: 'https://my-service.io/api/status/123',
        statusJsonPath: '$.status'])

    webhookService.getWebhookStatus(_) >> {
      throw new ResourceAccessException('I/O error on GET request for "https://my-service.io/api/status/123"', new SocketTimeoutException("timeout"))
    }

    when:
    def result = monitorWebhookTask.execute stage

    then:
    result.status == ExecutionStatus.RUNNING
  }

  @Unroll
  def "should be #expectedTaskStatus in case of #statusCode"() {
    setup:
    def stage = new StageExecutionImpl(pipeline, "webhook", [
        statusEndpoint: 'https://my-service.io/api/status/123',
        statusJsonPath: '$.status',
        successStatuses: 'SUCCESS',
        canceledStatuses: 'CANCELED',
        terminalStatuses: 'TERMINAL',
        retryStatusCodes: [404, 405]
    ])

    monitorWebhookTask.webhookProperties.defaultRetryStatusCodes = [429, 403]
    1 * webhookService.getWebhookStatus(_) >> { throw new HttpServerErrorException(statusCode, statusCode.name()) }

    when:
    def result = monitorWebhookTask.execute stage

    then:
    result.status == expectedTaskStatus

    where:
    statusCode                       | expectedTaskStatus
    HttpStatus.TOO_MANY_REQUESTS     | ExecutionStatus.RUNNING
    HttpStatus.NOT_FOUND             | ExecutionStatus.RUNNING
    HttpStatus.METHOD_NOT_ALLOWED    | ExecutionStatus.RUNNING
    HttpStatus.FORBIDDEN             | ExecutionStatus.RUNNING
    HttpStatus.INTERNAL_SERVER_ERROR | ExecutionStatus.RUNNING
    HttpStatus.NOT_ACCEPTABLE        | ExecutionStatus.TERMINAL
  }

  def "should do a get request to the defined statusEndpoint"() {
    setup:
    def headers = new HttpHeaders()
    headers.add(HttpHeaders.CONTENT_TYPE, "application/json")
    1 * webhookService.getWebhookStatus(_) >> new ResponseEntity<Map>([status: "RUNNING"], headers, HttpStatus.OK)

    def stage = new StageExecutionImpl(pipeline, "webhook", [
        statusEndpoint: 'https://my-service.io/api/status/123',
        statusJsonPath: '$.status',
        successStatuses: 'SUCCESS',
        canceledStatuses: 'CANCELED',
        terminalStatuses: 'TERMINAL',
        customHeaders: [Authorization: "Basic password"],
        someVariableWeDontCareAbout: 'Hello!'
    ])

    when:
    def result = monitorWebhookTask.execute stage

    then:
    result.status == ExecutionStatus.RUNNING
    result.context.webhook.monitor == new WebhookStage.WebhookMonitorResponseStageData(headers: ["Content-Type": "application/json"], body: [status: "RUNNING"], statusCode: HttpStatus.OK, statusCodeValue: HttpStatus.OK.value())
  }

  def "should find correct element using statusJsonPath parameter"() {
    setup:
    1 * webhookService.getWebhookStatus(_) >> new ResponseEntity<Map>([status: "TERMINAL"], HttpStatus.OK)

    def stage = new StageExecutionImpl(pipeline, "webhook", [
        statusEndpoint: 'https://my-service.io/api/status/123',
        statusJsonPath: '$.status',
        successStatuses: 'SUCCESS',
        canceledStatuses: 'CANCELED',
        terminalStatuses: 'TERMINAL',
        someVariableWeDontCareAbout: 'Hello!'
    ])

    when:
    def result = monitorWebhookTask.execute stage

    then:
    result.status == ExecutionStatus.TERMINAL
    result.context.webhook.monitor == new WebhookStage.WebhookMonitorResponseStageData(body: [status: "TERMINAL"], statusCode: HttpStatus.OK, statusCodeValue: HttpStatus.OK.value())
  }

  def "should return percentComplete if supported by endpoint"() {
    setup:
    1 * webhookService.getWebhookStatus(_) >> new ResponseEntity<Map>([status: 42], HttpStatus.OK)
    def stage = new StageExecutionImpl(pipeline, "webhook", [
        statusEndpoint: 'https://my-service.io/api/status/123',
        statusJsonPath: '$.status',
        successStatuses: 'SUCCESS',
        canceledStatuses: 'CANCELED',
        terminalStatuses: 'TERMINAL',
        someVariableWeDontCareAbout: 'Hello!',
        customHeaders: [:]
    ])

    when:
    def result = monitorWebhookTask.execute stage

    then:
    result.status == ExecutionStatus.RUNNING
    result.context.webhook.monitor == new WebhookStage.WebhookMonitorResponseStageData(percentComplete: 42, body: [status: 42], statusCode: HttpStatus.OK, statusCodeValue: HttpStatus.OK.value())
  }

  def "100 percent complete should result in SUCCEEDED status"() {
    setup:
    1 * webhookService.getWebhookStatus(_) >> new ResponseEntity<Map>([status: 100], HttpStatus.OK)

    def stage = new StageExecutionImpl(pipeline, "webhook", [
        statusEndpoint: 'https://my-service.io/api/status/123',
        statusJsonPath: '$.status',
        successStatuses: 'SUCCESS',
        canceledStatuses: 'CANCELED',
        terminalStatuses: 'TERMINAL',
        someVariableWeDontCareAbout: 'Hello!'
    ])

    when:
    def result = monitorWebhookTask.execute stage

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.context.webhook.monitor == new WebhookStage.WebhookMonitorResponseStageData(percentComplete: 100, body: [status: 100], statusCode: HttpStatus.OK, statusCodeValue: HttpStatus.OK.value())
  }

  def "should return TERMINAL status if jsonPath can not be found"() {
    setup:
    1 * webhookService.getWebhookStatus(_) >> new ResponseEntity<Map>([status: "SUCCESS"], HttpStatus.OK)
    def stage = new StageExecutionImpl(pipeline, "webhook", [
        statusEndpoint: 'https://my-service.io/api/status/123',
        statusJsonPath: '$.doesnt.exist',
        successStatuses: 'SUCCESS'
    ])

    when:
    def result = monitorWebhookTask.execute stage

    then:
    result.status == ExecutionStatus.TERMINAL
    result.context.webhook.monitor == new WebhookStage.WebhookMonitorResponseStageData(error: 'Unable to parse status: JSON property \'$.doesnt.exist\' not found in response body', body: [status: "SUCCESS"], statusCode: HttpStatus.OK, statusCodeValue: HttpStatus.OK.value())
  }

  def "should return TERMINAL status if jsonPath isn't evaluated to single value"() {
    setup:
    1 * webhookService.getWebhookStatus(_) >> new ResponseEntity<Map>([status: ["some", "complex", "list"]], HttpStatus.OK)
    def stage = new StageExecutionImpl(pipeline, "webhook", [
        statusEndpoint: 'https://my-service.io/api/status/123',
        statusJsonPath: '$.status',
        successStatuses: 'SUCCESS'
    ])

    when:
    def result = monitorWebhookTask.execute stage

    then:
    result.status == ExecutionStatus.TERMINAL
    result.context.webhook.monitor == new WebhookStage.WebhookMonitorResponseStageData(
        error: "The json path '\$.status' did not resolve to a single value",
        resolvedValue: ["some", "complex", "list"],
        body: [status: ["some", "complex", "list"]],
        statusCode: HttpStatus.OK,
        statusCodeValue: HttpStatus.OK.value()
    )
  }

  @Unroll
  def 'should return #expectedTaskStatus if #statusCode when #scenario'() {
    given:
    def stage = new StageExecutionImpl(pipeline, "webhook", [
        statusEndpoint: 'https://my-service.io/api/status/123',
        statusJsonPath: '$.status',
        successStatuses: 'SUCCESS',
        canceledStatuses: 'CANCELED',
        terminalStatuses: 'TERMINAL',
        retries: [404: [maxAttempts: 2], 504: [maxAttempts: 0]],
        webhook: [ monitor: [pastStatusCodes: pastStatuses]]
    ])

    monitorWebhookTask.webhookProperties.defaultRetryStatusCodes = [429, 403]
    1 * webhookService.getWebhookStatus(_) >> { throw new HttpServerErrorException(statusCode, statusCode.name()) }

    when:
    def result = monitorWebhookTask.execute stage

    then:
    result.status == expectedTaskStatus
    result.context.webhook.monitor?.pastStatusCodes == expectedPastStatuses

    where:
    scenario                                           | statusCode                   | pastStatuses    | expectedTaskStatus       | expectedPastStatuses
    'default retry is configured'                      | HttpStatus.TOO_MANY_REQUESTS | []              | ExecutionStatus.RUNNING  | [429]
    'no retry limit configured'                        | HttpStatus.NOT_FOUND         | []              | ExecutionStatus.RUNNING  | [404]
    '1 previous with max of 2'                         | HttpStatus.NOT_FOUND         | [404]           | ExecutionStatus.RUNNING  | [404,404]
    '2 previous with max of 2'                         | HttpStatus.NOT_FOUND         | [404, 404]      | ExecutionStatus.TERMINAL | pastStatuses
    '2 previous with max of 2 with intervening status' | HttpStatus.NOT_FOUND         | [404, 429, 404] | ExecutionStatus.TERMINAL | pastStatuses
    'empty past statuses'                              | HttpStatus.GATEWAY_TIMEOUT   | []              | ExecutionStatus.TERMINAL | pastStatuses
    'null past statuses'                               | HttpStatus.GATEWAY_TIMEOUT   | null            | ExecutionStatus.TERMINAL | pastStatuses
  }
}
