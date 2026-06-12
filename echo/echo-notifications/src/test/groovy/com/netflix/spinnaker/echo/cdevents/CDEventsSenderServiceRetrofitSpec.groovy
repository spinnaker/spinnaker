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

import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import io.cloudevents.core.builder.CloudEventBuilder
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import spock.lang.Specification
import spock.lang.Subject

import java.net.URI

/**
 * Tests for CDEventsSenderClient to ensure proper Retrofit 2 handling after the fix.
 * Verifies that the client returns Call<ResponseBody> (not Call<Response<ResponseBody>>)
 * and that Retrofit2SyncCall.executeCall() properly returns Response<ResponseBody>.
 *
 * The bug was that Call<Response<ResponseBody>> was attempting to deserialize the
 * broker response into a retrofit2.Response object, which has no default constructor
 * and caused Jackson to fail with "Cannot construct instance of retrofit2.Response".
 */
class CDEventsSenderServiceRetrofitSpec extends Specification {

  MockWebServer server
  @Subject
  CDEventsSenderClient cdEventsSenderClient
  CDEventsConverterFactory converterFactory

  def setup() {
    server = new MockWebServer()
    server.start()

    converterFactory = CDEventsConverterFactory.create()

    // Create a retrofit client for testing
    def retrofit = new Retrofit.Builder()
      .baseUrl(server.url("/"))
      .client(new OkHttpClient())
      .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
      .addConverterFactory(converterFactory)
      .build()

    cdEventsSenderClient = retrofit.create(CDEventsSenderClient)
  }

  def cleanup() {
    server.shutdown()
  }

  def "sendCDEvent returns Call<ResponseBody> that executeCall converts to Response<ResponseBody>"() {
    given: "CDEvents broker returns success"
    def responseJson = '''
      {
        "status": "accepted",
        "id": "event-123"
      }
    '''
    server.enqueue(new MockResponse()
      .setResponseCode(200)
      .setHeader("Content-Type", "application/json")
      .setBody(responseJson))

    def cloudEvent = CloudEventBuilder.v1()
      .withId("test-event-123")
      .withSource(URI.create("/spinnaker/echo"))
      .withType("dev.cdevents.pipelinerun.started.0.1.1")
      .withData("application/json", '{"test":"data"}'.bytes)
      .build()

    def jsonEvent = converterFactory.convertCDEventToJson(cloudEvent)

    when: "sending CDEvent using executeCall"
    def response = Retrofit2SyncCall.executeCall(
      cdEventsSenderClient.sendCDEvent(jsonEvent, "default/events-broker"))

    then: "response is successful and returns Response<ResponseBody> type"
    response != null
    response.isSuccessful()
    response.code() == 200
    response.body() != null
    response.body().string().contains("accepted")
  }

  def "sendCDEvent handles broker error response without deserialization exception"() {
    given: "CDEvents broker returns an error"
    def errorJson = '''
      {
        "error": "validation_failed",
        "message": "Invalid CDEvent format"
      }
    '''
    server.enqueue(new MockResponse()
      .setResponseCode(400)
      .setHeader("Content-Type", "application/json")
      .setBody(errorJson))

    def cloudEvent = CloudEventBuilder.v1()
      .withId("test-event-456")
      .withSource(URI.create("/spinnaker/echo"))
      .withType("dev.cdevents.taskrun.finished.0.1.1")
      .build()

    def jsonEvent = converterFactory.convertCDEventToJson(cloudEvent)

    when: "sending CDEvent that fails validation"
    Retrofit2SyncCall.executeCall(
      cdEventsSenderClient.sendCDEvent(jsonEvent, "events"))

    then: "ErrorHandlingExecutorCallAdapterFactory throws SpinnakerHttpException for 4xx errors"
    def e = thrown(com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException)
    e.responseCode == 400
    // The key point: no SpinnakerConversionException about "Cannot construct instance of retrofit2.Response"
  }

  def "sendCDEvent handles empty response body"() {
    given: "CDEvents broker returns empty response"
    server.enqueue(new MockResponse()
      .setResponseCode(204))

    def cloudEvent = CloudEventBuilder.v1()
      .withId("empty-response-test")
      .withSource(URI.create("/spinnaker/echo"))
      .withType("dev.cdevents.taskrun.started.0.1.1")
      .build()

    def jsonEvent = converterFactory.convertCDEventToJson(cloudEvent)

    when: "sending CDEvent"
    def response = Retrofit2SyncCall.executeCall(
      cdEventsSenderClient.sendCDEvent(jsonEvent, "events"))

    then: "response is successful with empty body"
    response != null
    response.isSuccessful()
    response.code() == 204
  }

  def "sendCDEvent properly encodes broker URL path"() {
    given: "CDEvents broker URL with path segments"
    server.enqueue(new MockResponse()
      .setResponseCode(200)
      .setBody('{"status":"ok"}'))

    def cloudEvent = CloudEventBuilder.v1()
      .withId("url-parsing-test")
      .withSource(URI.create("/spinnaker/echo"))
      .withType("dev.cdevents.pipelinerun.finished.0.1.1")
      .build()

    def jsonEvent = converterFactory.convertCDEventToJson(cloudEvent)

    when: "sending CDEvent with complex path"
    Retrofit2SyncCall.executeCall(
      cdEventsSenderClient.sendCDEvent(jsonEvent, "namespace/events/broker"))
    def recordedRequest = server.takeRequest()

    then: "request is sent to correct path"
    recordedRequest.path == "/namespace/events/broker"
  }

  def "sendCDEvent Response<ResponseBody> exposes code, message, and body for callers"() {
    given: "CDEvents broker returns detailed response"
    def responseJson = '''
      {
        "event_id": "processed-123",
        "status": "accepted",
        "broker_info": {
          "name": "kafka",
          "topic": "cdevents"
        }
      }
    '''
    server.enqueue(new MockResponse()
      .setResponseCode(201)
      .setHeader("Content-Type", "application/json")
      .setBody(responseJson))

    def cloudEvent = CloudEventBuilder.v1()
      .withId("agent-compat-test")
      .withSource(URI.create("/spinnaker/echo"))
      .withType("dev.cdevents.pipelinerun.started.0.1.1")
      .build()

    def jsonEvent = converterFactory.convertCDEventToJson(cloudEvent)

    when: "sending CDEvent and reading response like CDEventsNotificationAgent does"
    def response = Retrofit2SyncCall.executeCall(
      cdEventsSenderClient.sendCDEvent(jsonEvent, "events"))

    then: "response exposes code(), message(), and body() for agent consumption"
    response.code() == 201
    response.message() != null
    response.body() != null
    def bodyContent = response.body().string()
    bodyContent.contains("processed-123")
    bodyContent.contains("kafka")
  }

  def "sendCDEvent handles CloudEvents binary content mode response"() {
    given: "CDEvents broker returns response with CloudEvents headers"
    server.enqueue(new MockResponse()
      .setResponseCode(200)
      .setHeader("Content-Type", "application/json")
      .setHeader("ce-id", "response-event-123")
      .setHeader("ce-source", "/broker/events")
      .setHeader("ce-type", "com.broker.event.accepted")
      .setBody('{"acknowledged": true}'))

    def cloudEvent = CloudEventBuilder.v1()
      .withId("test-123")
      .withSource(URI.create("/spinnaker/echo"))
      .withType("dev.cdevents.pipelinerun.started.0.1.1")
      .build()

    def jsonEvent = converterFactory.convertCDEventToJson(cloudEvent)

    when: "sending CDEvent"
    def response = Retrofit2SyncCall.executeCall(
      cdEventsSenderClient.sendCDEvent(jsonEvent, "events"))

    then: "response includes CloudEvents headers"
    response.isSuccessful()
    response.headers().get("ce-id") == "response-event-123"
    response.headers().get("ce-type") == "com.broker.event.accepted"
  }

  def "sendCDEvent handles complex JSON response without deserialization error"() {
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
        }
      }
    '''
    server.enqueue(new MockResponse()
      .setResponseCode(200)
      .setHeader("Content-Type", "application/json")
      .setBody(responseJson))

    def cloudEvent = CloudEventBuilder.v1()
      .withId("pipeline-finished-123")
      .withSource(URI.create("/spinnaker/echo"))
      .withType("dev.cdevents.pipelinerun.finished.0.1.1")
      .build()

    def jsonEvent = converterFactory.convertCDEventToJson(cloudEvent)

    when: "sending CDEvent"
    def response = Retrofit2SyncCall.executeCall(
      cdEventsSenderClient.sendCDEvent(jsonEvent, "v1/events"))

    then: "response body is ResponseBody, not retrofit2.Response"
    response != null
    response.isSuccessful()
    response.body() != null
    def body = response.body().string()
    body.contains("kafka-broker")
    body.contains("schema_version")
    body.contains("cdevent-987654")
  }
}
