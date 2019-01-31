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
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.webhook.service.WebhookService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class MonitorWebhookTaskSpec extends Specification {

  def pipeline = Execution.newPipeline("orca")

  @Subject
  MonitorWebhookTask monitorWebhookTask = new MonitorWebhookTask()

  @Unroll
  def "should fail if required parameter #parameter is missing"() {
    setup:
    def stage = new Stage(pipeline, "webhook", [
      statusEndpoint: 'https://my-service.io/api/status/123',
      statusJsonPath: '$.status',
      successStatuses: 'SUCCESS',
      canceledStatuses: 'CANCELED',
      terminalStatuses: 'TERMINAL',
      someVariableWeDontCareAbout: 'Hello!'
    ])

    when:
    stage.context.remove parameter
    monitorWebhookTask.execute stage

    then:
    def ex = thrown IllegalStateException
    ex.message == "Missing required parameter '${parameter}'" as String

    where:
    parameter << MonitorWebhookTask.requiredParameters
  }

  def "should fail if no parameters are supplied"() {
    setup:
    def stage = new Stage(pipeline, "webhook", [:])

    when:
    monitorWebhookTask.execute stage

    then:
    def ex = thrown IllegalStateException
    ex.message == "Missing required parameters 'statusEndpoint', 'statusJsonPath'" as String
  }

  def "should do a get request to the defined statusEndpoint"() {
    setup:
    monitorWebhookTask.webhookService = Mock(WebhookService) {
      1 * getStatus("https://my-service.io/api/status/123", [Authorization: "Basic password"]) >> new ResponseEntity<Map>([status:"RUNNING"], HttpStatus.OK)
    }
    def stage = new Stage(pipeline, "webhook", [
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
    result.context.webhook.monitor == [body: [status: "RUNNING"], statusCode: HttpStatus.OK, statusCodeValue: HttpStatus.OK.value()]
  }

  def "should find correct element using statusJsonPath parameter"() {
    setup:
    monitorWebhookTask.webhookService = Mock(WebhookService) {
      1 * getStatus("https://my-service.io/api/status/123", null) >> new ResponseEntity<Map>([status:"TERMINAL"], HttpStatus.OK)
    }
    def stage = new Stage(pipeline, "webhook", [
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
    result.context.webhook.monitor == [body: [status: "TERMINAL"], statusCode: HttpStatus.OK, statusCodeValue: HttpStatus.OK.value()]
  }

  def "should return percentComplete if supported by endpoint"() {
    setup:
    monitorWebhookTask.webhookService = Mock(WebhookService) {
      1 * getStatus("https://my-service.io/api/status/123", [:]) >> new ResponseEntity<Map>([status:42], HttpStatus.OK)
    }
    def stage = new Stage(pipeline, "webhook", [
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
    result.context.webhook.monitor == [percentComplete: 42, body: [status: 42], statusCode: HttpStatus.OK, statusCodeValue: HttpStatus.OK.value()]
  }

  def "100 percent complete should result in SUCCEEDED status"() {
    setup:
    monitorWebhookTask.webhookService = Mock(WebhookService) {
      1 * getStatus("https://my-service.io/api/status/123", null) >> new ResponseEntity<Map>([status:100], HttpStatus.OK)
    }
    def stage = new Stage(pipeline, "webhook", [
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
    result.context.webhook.monitor == [percentComplete: 100, body: [status: 100], statusCode: HttpStatus.OK, statusCodeValue: HttpStatus.OK.value()]
  }

  def "should return TERMINAL status if jsonPath can not be found"() {
    setup:
    monitorWebhookTask.webhookService = Mock(WebhookService) {
      1 * getStatus("https://my-service.io/api/status/123", null) >> new ResponseEntity<Map>([status:"SUCCESS"], HttpStatus.OK)
    }
    def stage = new Stage(pipeline, "webhook", [
      statusEndpoint: 'https://my-service.io/api/status/123',
      statusJsonPath: '$.doesnt.exist',
      successStatuses: 'SUCCESS'
    ])

    when:
    def result = monitorWebhookTask.execute stage

    then:
    result.status == ExecutionStatus.TERMINAL
    result.context.webhook.monitor == [error: 'Unable to parse status: JSON property \'$.doesnt.exist\' not found in response body', body: [status: "SUCCESS"], statusCode: HttpStatus.OK, statusCodeValue: HttpStatus.OK.value()]
  }

  def "should return TERMINAL status if jsonPath isn't evaluated to single value"() {
    setup:
    monitorWebhookTask.webhookService = Mock(WebhookService) {
      1 * getStatus("https://my-service.io/api/status/123", null) >> new ResponseEntity<Map>([status:["some", "complex", "list"]], HttpStatus.OK)
    }
    def stage = new Stage(pipeline, "webhook", [
      statusEndpoint: 'https://my-service.io/api/status/123',
      statusJsonPath: '$.status',
      successStatuses: 'SUCCESS'
    ])

    when:
    def result = monitorWebhookTask.execute stage

    then:
    result.status == ExecutionStatus.TERMINAL
    result.context.webhook.monitor == [
      error: "The json path '\$.status' did not resolve to a single value",
      resolvedValue: ["some", "complex", "list"],
      body: [status: ["some", "complex", "list"]],
      statusCode: HttpStatus.OK,
      statusCodeValue: HttpStatus.OK.value()
    ]
  }
}
