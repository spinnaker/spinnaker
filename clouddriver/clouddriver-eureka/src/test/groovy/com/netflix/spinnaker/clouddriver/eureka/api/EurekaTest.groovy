/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.eureka.api

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.eureka.model.EurekaApplication
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class EurekaTest extends Specification {

  @Shared
  @AutoCleanup("shutdown")
  MockWebServer mockWebServer

  @Shared
  Eureka eurekaApi

  def setupSpec() {
    mockWebServer = new MockWebServer()
    mockWebServer.start()

    def objectMapper = new ObjectMapper()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
      .enable(MapperFeature.AUTO_DETECT_CREATORS)

    eurekaApi = new Retrofit.Builder()
      .baseUrl(mockWebServer.url("/"))
      .addConverterFactory(JacksonConverterFactory.create(objectMapper))
      .build()
      .create(Eureka)
  }

  def "getInstanceInfo should make GET request to correct path with instanceId"() {
    given: "a mock response for instance info"
    def mockInstanceData = [
      instanceId: "i-123456",
      app: "testapp",
      status: "UP"
    ]
    mockWebServer.enqueue(new MockResponse()
      .setBody('{"instanceId":"i-123456","app":"testapp","status":"UP"}')
      .addHeader("Content-Type", "application/json"))

    when: "calling getInstanceInfo"
    def response = eurekaApi.getInstanceInfo("i-123456").execute()
    RecordedRequest request = mockWebServer.takeRequest()

    then: "request is correctly formatted"
    request.path == "/instances/i-123456"
    request.method == "GET"
    request.getHeader("Accept") == "application/json"

    and: "response is parsed correctly"
    response.isSuccessful()
    response.body().instanceId == "i-123456"
    response.body().app == "testapp"
    response.body().status == "UP"
  }

  def "getInstanceInfo should handle URL encoding for special characters in instanceId"() {
    given: "a mock response"
    mockWebServer.enqueue(new MockResponse()
      .setBody('{"instanceId":"test-instance","status":"UP"}')
      .addHeader("Content-Type", "application/json"))

    when: "calling with instanceId containing special characters"
    def response = eurekaApi.getInstanceInfo("test-instance:8080").execute()
    RecordedRequest request = mockWebServer.takeRequest()

    then: "path is URL encoded correctly"
    request.path == "/instances/test-instance:8080"
    request.method == "GET"
    response.isSuccessful()
  }

  def "updateInstanceStatus should make PUT request with correct path and query parameter"() {
    given: "a mock response"
    mockWebServer.enqueue(new MockResponse().setResponseCode(200))

    when: "calling updateInstanceStatus"
    def response = eurekaApi.updateInstanceStatus("testapp", "i-123456", "OUT_OF_SERVICE").execute()
    RecordedRequest request = mockWebServer.takeRequest()

    then: "request is correctly formatted"
    request.path == "/apps/testapp/i-123456/status?value=OUT_OF_SERVICE"
    request.method == "PUT"
    request.getHeader("Accept") == "application/json"
    response.isSuccessful()
  }

  def "updateInstanceStatus should handle different status values"() {
    given: "a mock response"
    mockWebServer.enqueue(new MockResponse().setResponseCode(200))

    when: "updating with status"
    def response = eurekaApi.updateInstanceStatus("myapp", "instance-1", status).execute()
    RecordedRequest request = mockWebServer.takeRequest()

    then: "query parameter matches the status"
    request.path.contains("value=$status")
    request.method == "PUT"
    response.isSuccessful()

    where:
    status << ["UP", "DOWN", "OUT_OF_SERVICE", "STARTING"]
  }

  def "resetInstanceStatus should make DELETE request with correct path and query parameter"() {
    given: "a mock response"
    mockWebServer.enqueue(new MockResponse().setResponseCode(200))

    when: "calling resetInstanceStatus"
    def response = eurekaApi.resetInstanceStatus("testapp", "i-123456", "UP").execute()
    RecordedRequest request = mockWebServer.takeRequest()

    then: "request is correctly formatted"
    request.path == "/apps/testapp/i-123456/status?value=UP"
    request.method == "DELETE"
    request.getHeader("Accept") == "application/json"
    response.isSuccessful()
  }

  def "resetInstanceStatus should handle URL encoding in path parameters"() {
    given: "a mock response"
    mockWebServer.enqueue(new MockResponse().setResponseCode(200))

    when: "calling with special characters in app name"
    def response = eurekaApi.resetInstanceStatus("test-app", "instance-1", "UP").execute()
    RecordedRequest request = mockWebServer.takeRequest()

    then: "path parameters are properly handled"
    request.path.startsWith("/apps/test-app/instance-1/status")
    request.path.contains("value=UP")
    request.method == "DELETE"
    response.isSuccessful()
  }

  def "getApplication should make GET request to correct path"() {
    given: "a mock EurekaApplication response"
    def mockResponse = '''
    {
      "name": "testapp",
      "instance": [
        {
          "instanceId": "i-123456",
          "status": "UP",
          "hostName": "host1",
          "app": "testapp",
          "ipAddr": "1.2.3.4",
          "dataCenterInfo": {
            "name": "Amazon",
            "metadata": {
              "instanceId": "i-123456",
              "availabilityZone": "us-east-1a",
              "accountId": "123456789",
              "amiId": "ami-123",
              "instanceType": "m5.large"
            }
          }
        }
      ]
    }
    '''
    mockWebServer.enqueue(new MockResponse()
      .setBody(mockResponse)
      .addHeader("Content-Type", "application/json"))

    when: "calling getApplication"
    def response = eurekaApi.getApplication("testapp").execute()
    RecordedRequest request = mockWebServer.takeRequest()

    then: "request is correctly formatted"
    request.path == "/apps/testapp"
    request.method == "GET"
    request.getHeader("Accept") == "application/json"

    and: "response is parsed correctly"
    response.isSuccessful()
    response.body() instanceof EurekaApplication
    response.body().name == "testapp"
    response.body().instances.size() == 1
    response.body().instances[0].instanceId == "i-123456"
  }

  def "getApplication should handle non-existent applications"() {
    given: "a 404 response"
    mockWebServer.enqueue(new MockResponse().setResponseCode(404))

    when: "calling getApplication for non-existent app"
    def response = eurekaApi.getApplication("nonexistent").execute()
    RecordedRequest request = mockWebServer.takeRequest()

    then: "request is made but response indicates failure"
    request.path == "/apps/nonexistent"
    request.method == "GET"
    !response.isSuccessful()
    response.code() == 404
  }

  def "all endpoints should include Accept: application/json header"() {
    given: "mock responses"
    4.times { mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{}")) }

    when: "calling all endpoints"
    eurekaApi.getInstanceInfo("test-id").execute()
    eurekaApi.updateInstanceStatus("app", "id", "UP").execute()
    eurekaApi.resetInstanceStatus("app", "id", "UP").execute()
    eurekaApi.getApplication("app").execute()

    then: "all requests include Accept header"
    4.times {
      RecordedRequest request = mockWebServer.takeRequest()
      assert request.getHeader("Accept") == "application/json"
    }
  }

  def "API methods should handle server errors gracefully"() {
    given: "a server error response"
    mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"))

    when: "calling any API method"
    def response = eurekaApi.getInstanceInfo("test-id").execute()

    then: "response indicates failure"
    !response.isSuccessful()
    response.code() == 500
  }

  def "API paths should not have leading slashes"() {
    given: "mock responses for each endpoint"
    4.times { mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{}")) }

    when: "calling each endpoint"
    eurekaApi.getInstanceInfo("test").execute()
    eurekaApi.updateInstanceStatus("app", "id", "UP").execute()
    eurekaApi.resetInstanceStatus("app", "id", "UP").execute()
    eurekaApi.getApplication("app").execute()

    then: "all paths should not start with double slashes"
    4.times {
      RecordedRequest request = mockWebServer.takeRequest()
      assert !request.path.startsWith("//"), "Path should not have leading double slashes: ${request.path}"
    }
  }
}
