/*
    Copyright (C) 2026 Harness, Inc.
    For a full list of individual contributors, please see the commit history.
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
          http://www.apache.org/licenses/LICENSE-2.0
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    SPDX-License-Identifier: Apache-2.0
*/

package com.netflix.spinnaker.echo.cdevents

import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import spock.lang.Specification
import spock.lang.Subject

/**
 * Tests for CDEventsSenderClient to ensure proper Retrofit 2 handling after the fix for
 * SpinnakerConversionException when sendCDEvent return type was corrected from
 * Call<Response<ResponseBody>> to Call<ResponseBody>.
 */
class CDEventsSenderClientRetrofitSpec extends Specification {

  MockWebServer server
  @Subject
  CDEventsSenderClient cdEventsSenderClient

  def setup() {
    server = new MockWebServer()
    server.start()

    def retrofit = new Retrofit.Builder()
      .baseUrl(server.url("/"))
      .addConverterFactory(JacksonConverterFactory.create())
      .build()

    cdEventsSenderClient = retrofit.create(CDEventsSenderClient)
  }

  def cleanup() {
    server.shutdown()
  }

  def "sendCDEvent should handle successful broker response with JSON body"() {
    given: "CDEvents broker returns a success response"
    def responseJson = '''
      {
        "status": "accepted",
        "id": "event-123",
        "timestamp": "2026-06-11T10:00:00Z"
      }
    '''
    server.enqueue(new MockResponse()
      .setResponseCode(200)
      .setHeader("Content-Type", "application/json")
      .setBody(responseJson))

    def cdEventJson = '''
      {
        "context": {
          "version": "0.4.0",
          "id": "test-event-id",
          "source": "/spinnaker/echo",
          "type": "dev.cdevents.pipelinerun.started.0.1.1",
          "timestamp": "2026-06-11T10:00:00Z"
        },
        "subject": {
          "id": "pipeline-123",
          "type": "pipelineRun"
        }
      }
    '''

    when: "sending CDEvent"
    def response = Retrofit2SyncCall.execute(cdEventsSenderClient.sendCDEvent(cdEventJson, "default/events-broker"))

    then: "response is successful and body can be read"
    response != null
    def responseBody = response.string()
    responseBody.contains("accepted")
    responseBody.contains("event-123")
  }

  def "sendCDEvent should handle broker error response without deserialization exception"() {
    given: "CDEvents broker returns an error"
    def errorJson = '''
      {
        "error": "invalid_event",
        "message": "CDEvent schema validation failed"
      }
    '''
    server.enqueue(new MockResponse()
      .setResponseCode(400)
      .setHeader("Content-Type", "application/json")
      .setBody(errorJson))

    def invalidEventJson = '{"invalid": "event"}'

    when: "sending invalid CDEvent"
    def response = Retrofit2SyncCall.executeCall(cdEventsSenderClient.sendCDEvent(invalidEventJson, "default/events-broker"))

    then: "response indicates failure without throwing deserialization exception"
    response != null
    !response.isSuccessful()
    response.code() == 400
    response.errorBody() != null
  }

  def "sendCDEvent should handle empty response body from broker"() {
    given: "CDEvents broker returns empty response"
    server.enqueue(new MockResponse()
      .setResponseCode(202)
      .setHeader("Content-Type", "application/json"))

    def cdEventJson = '''
      {
        "context": {
          "type": "dev.cdevents.taskrun.finished.0.1.1"
        }
      }
    '''

    when: "sending CDEvent"
    def response = Retrofit2SyncCall.executeCall(cdEventsSenderClient.sendCDEvent(cdEventJson, "events"))

    then: "response is successful without throwing deserialization exception"
    response != null
    response.isSuccessful()
    response.code() == 202
  }

  def "sendCDEvent should not attempt to deserialize response into retrofit2.Response type"() {
    given: "CDEvents broker returns a complex JSON response"
    def responseJson = '''
      {
        "id": "cdevent-987654",
        "status": "processed",
        "broker": {
          "name": "kafka-broker",
          "topic": "cdevents",
          "partition": 0,
          "offset": 12345
        },
        "validation": {
          "schema_version": "0.4.0",
          "valid": true
        },
        "metadata": {
          "received_at": "2026-06-11T10:00:00.123Z",
          "processed_at": "2026-06-11T10:00:00.456Z"
        }
      }
    '''
    server.enqueue(new MockResponse()
      .setResponseCode(200)
      .setHeader("Content-Type", "application/json")
      .setBody(responseJson))

    def cdEventJson = '''
      {
        "context": {
          "type": "dev.cdevents.pipelinerun.finished.0.1.1",
          "id": "pipeline-finished-123"
        }
      }
    '''

    when: "sending CDEvent with executeCall"
    def response = Retrofit2SyncCall.executeCall(cdEventsSenderClient.sendCDEvent(cdEventJson, "v1/events"))

    then: "response body is ResponseBody, not retrofit2.Response"
    response != null
    response.isSuccessful()
    response.body() != null
    def bodyString = response.body().string()
    bodyString.contains("kafka-broker")
    bodyString.contains("schema_version")
    bodyString.contains("cdevent-987654")
  }

  def "sendCDEvent should handle 5xx server errors from broker"() {
    given: "CDEvents broker returns a server error"
    server.enqueue(new MockResponse()
      .setResponseCode(503)
      .setHeader("Content-Type", "text/plain")
      .setBody("Service Unavailable"))

    def cdEventJson = '{"context": {"type": "dev.cdevents.taskrun.started.0.1.1"}}'

    when: "sending CDEvent"
    def response = Retrofit2SyncCall.executeCall(cdEventsSenderClient.sendCDEvent(cdEventJson, "broker"))

    then: "response indicates server error"
    response != null
    !response.isSuccessful()
    response.code() == 503
  }

  def "sendCDEvent should properly encode broker URL path"() {
    given: "CDEvents broker path with special characters"
    server.enqueue(new MockResponse()
      .setResponseCode(200)
      .setBody('{"status": "ok"}'))

    def cdEventJson = '{"context": {"type": "dev.cdevents.pipelinerun.queued.0.1.1"}}'

    when: "sending CDEvent with encoded path"
    def response = Retrofit2SyncCall.executeCall(cdEventsSenderClient.sendCDEvent(cdEventJson, "namespace/sub-path/events"))

    then: "request is successful"
    response.isSuccessful()
    def request = server.takeRequest()
    request.path.contains("namespace/sub-path/events")
  }

  def "sendCDEvent should handle CloudEvents binary content mode response"() {
    given: "CDEvents broker returns response with CloudEvents headers"
    server.enqueue(new MockResponse()
      .setResponseCode(200)
      .setHeader("Content-Type", "application/json")
      .setHeader("ce-id", "response-event-123")
      .setHeader("ce-source", "/broker/events")
      .setHeader("ce-type", "com.broker.event.accepted")
      .setBody('{"acknowledged": true}'))

    def cdEventJson = '''
      {
        "context": {
          "id": "test-123",
          "type": "dev.cdevents.pipelinerun.started.0.1.1"
        }
      }
    '''

    when: "sending CDEvent"
    def response = Retrofit2SyncCall.executeCall(cdEventsSenderClient.sendCDEvent(cdEventJson, "events"))

    then: "response includes CloudEvents headers"
    response.isSuccessful()
    response.headers().get("ce-id") == "response-event-123"
    response.headers().get("ce-type") == "com.broker.event.accepted"
  }
}
