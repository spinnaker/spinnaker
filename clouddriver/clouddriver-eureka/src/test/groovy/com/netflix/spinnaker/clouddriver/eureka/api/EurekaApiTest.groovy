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
import com.netflix.spinnaker.clouddriver.eureka.model.EurekaApplications
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class EurekaApiTest extends Specification {

  @Shared
  @AutoCleanup("shutdown")
  MockWebServer mockWebServer

  @Shared
  EurekaApi eurekaApi

  def setupSpec() {
    mockWebServer = new MockWebServer()
    mockWebServer.start()

    def objectMapper = new ObjectMapper()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
      .enable(DeserializationFeature.UNWRAP_ROOT_VALUE)
      .enable(MapperFeature.AUTO_DETECT_CREATORS)

    eurekaApi = new Retrofit.Builder()
      .baseUrl(mockWebServer.url("/"))
      .addConverterFactory(JacksonConverterFactory.create(objectMapper))
      .build()
      .create(EurekaApi)
  }

  def "loadEurekaApplications should make GET request to /apps"() {
    given: "a mock EurekaApplications response"
    def mockResponse = '''
    {
      "applications": {
        "versions__delta": 1,
        "apps__hashcode": "UP_1_",
        "application": [
          {
            "name": "APP1",
            "instance": []
          },
          {
            "name": "APP2",
            "instance": []
          }
        ]
      }
    }
    '''
    mockWebServer.enqueue(new MockResponse()
      .setBody(mockResponse)
      .addHeader("Content-Type", "application/json"))

    when: "calling loadEurekaApplications"
    def response = eurekaApi.loadEurekaApplications().execute()
    RecordedRequest request = mockWebServer.takeRequest()

    then: "request is correctly formatted"
    request.path == "/apps"
    request.method == "GET"
    request.getHeader("Accept") == "application/json"

    and: "response is parsed correctly"
    response.isSuccessful()
    response.body() instanceof EurekaApplications
    response.body().applications.size() == 2
    response.body().applications[0].name == "APP1"
    response.body().applications[1].name == "APP2"
  }

  def "loadEurekaApplications should include Accept: application/json header"() {
    given: "a mock response"
    mockWebServer.enqueue(new MockResponse()
      .setBody('{"applications":{"application":[]}}')
      .addHeader("Content-Type", "application/json"))

    when: "calling loadEurekaApplications"
    eurekaApi.loadEurekaApplications().execute()
    RecordedRequest request = mockWebServer.takeRequest()

    then: "Accept header is present"
    request.getHeader("Accept") == "application/json"
  }

  def "loadEurekaApplications should handle empty application list"() {
    given: "a mock response with no applications"
    def mockResponse = '''
    {
      "applications": {
        "versions__delta": 1,
        "apps__hashcode": "UP_0_",
        "application": []
      }
    }
    '''
    mockWebServer.enqueue(new MockResponse()
      .setBody(mockResponse)
      .addHeader("Content-Type", "application/json"))

    when: "calling loadEurekaApplications"
    def response = eurekaApi.loadEurekaApplications().execute()

    then: "response contains empty list"
    response.isSuccessful()
    response.body() instanceof EurekaApplications
    response.body().applications.isEmpty()
  }

  def "loadEurekaApplications should handle application with instances"() {
    given: "a mock response with application containing instances"
    def mockResponse = '''
    {
      "applications": {
        "versions__delta": 1,
        "apps__hashcode": "UP_2_",
        "application": [
          {
            "name": "TESTAPP",
            "instance": [
              {
                "instanceId": "i-123456",
                "hostName": "test-host-1",
                "app": "TESTAPP",
                "ipAddr": "10.0.0.1",
                "status": "UP",
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
              },
              {
                "instanceId": "i-789012",
                "hostName": "test-host-2",
                "app": "TESTAPP",
                "ipAddr": "10.0.0.2",
                "status": "UP",
                "dataCenterInfo": {
                  "name": "Amazon",
                  "metadata": {
                    "instanceId": "i-789012",
                    "availabilityZone": "us-east-1b",
                    "accountId": "123456789",
                    "amiId": "ami-123",
                    "instanceType": "m5.large"
                  }
                }
              }
            ]
          }
        ]
      }
    }
    '''
    mockWebServer.enqueue(new MockResponse()
      .setBody(mockResponse)
      .addHeader("Content-Type", "application/json"))

    when: "calling loadEurekaApplications"
    def response = eurekaApi.loadEurekaApplications().execute()

    then: "applications and instances are parsed correctly"
    response.isSuccessful()
    def apps = response.body()
    apps.applications.size() == 1
    apps.applications[0].name == "TESTAPP"
    apps.applications[0].instances.size() == 2
    apps.applications[0].instances[0].instanceId == "i-123456"
    apps.applications[0].instances[1].instanceId == "i-789012"
  }

  def "loadEurekaApplications should handle metadata fields"() {
    given: "a mock response with metadata"
    def mockResponse = '''
    {
      "applications": {
        "versions__delta": 42,
        "apps__hashcode": "UP_3_DOWN_1_",
        "application": []
      }
    }
    '''
    mockWebServer.enqueue(new MockResponse()
      .setBody(mockResponse)
      .addHeader("Content-Type", "application/json"))

    when: "calling loadEurekaApplications"
    def response = eurekaApi.loadEurekaApplications().execute()

    then: "metadata fields are parsed correctly"
    response.isSuccessful()
    response.body().versionsDelta == 42
    response.body().appsHashCode == "UP_3_DOWN_1_"
  }

  def "loadEurekaApplications should handle server errors"() {
    given: "a server error response"
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(500)
      .setBody("Internal Server Error"))

    when: "calling loadEurekaApplications"
    def response = eurekaApi.loadEurekaApplications().execute()

    then: "response indicates failure"
    !response.isSuccessful()
    response.code() == 500
  }

  def "loadEurekaApplications should handle service unavailable"() {
    given: "a service unavailable response"
    mockWebServer.enqueue(new MockResponse()
      .setResponseCode(503)
      .setBody("Service Unavailable"))

    when: "calling loadEurekaApplications"
    def response = eurekaApi.loadEurekaApplications().execute()

    then: "response indicates service unavailable"
    !response.isSuccessful()
    response.code() == 503
  }

  def "loadEurekaApplications should handle network timeout"() {
    given: "a delayed response that exceeds timeout"
    def tempMockWebServer = new MockWebServer()
    tempMockWebServer.start()

    tempMockWebServer.enqueue(new MockResponse()
      .setBody('{"applications":{"application":[]}}')
      .setBodyDelay(3, java.util.concurrent.TimeUnit.SECONDS))

    when: "calling loadEurekaApplications with short timeout"
    def objectMapper = new ObjectMapper()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
      .enable(DeserializationFeature.UNWRAP_ROOT_VALUE)
      .enable(MapperFeature.AUTO_DETECT_CREATORS)

    def shortTimeoutRetrofit = new Retrofit.Builder()
      .baseUrl(tempMockWebServer.url("/"))
      .addConverterFactory(JacksonConverterFactory.create(objectMapper))
      .client(new okhttp3.OkHttpClient.Builder()
        .readTimeout(100, java.util.concurrent.TimeUnit.MILLISECONDS)
        .build())
      .build()
    def shortTimeoutApi = shortTimeoutRetrofit.create(EurekaApi)

    try {
      shortTimeoutApi.loadEurekaApplications().execute()
    } finally {
      tempMockWebServer.shutdown()
    }

    then: "throws timeout exception"
    thrown(java.net.SocketTimeoutException)
  }

  def "API path should not have leading slash"() {
    given: "a mock response"
    mockWebServer.enqueue(new MockResponse()
      .setBody('{"applications":{"application":[]}}')
      .addHeader("Content-Type", "application/json"))

    when: "calling loadEurekaApplications"
    eurekaApi.loadEurekaApplications().execute()
    RecordedRequest request = mockWebServer.takeRequest()

    then: "path should not start with double slashes"
    !request.path.startsWith("//")
    request.path == "/apps"
  }

  def "loadEurekaApplications should parse complex nested structure"() {
    given: "a complex mock response"
    def mockResponse = '''
    {
      "applications": {
        "versions__delta": 1,
        "apps__hashcode": "UP_5_",
        "application": [
          {
            "name": "SERVICE-A",
            "instance": [
              {
                "instanceId": "a-1",
                "status": "UP",
                "hostName": "host-a-1",
                "app": "SERVICE-A",
                "ipAddr": "10.0.0.1",
                "dataCenterInfo": {
                  "name": "Amazon",
                  "metadata": {
                    "instanceId": "a-1",
                    "availabilityZone": "us-east-1a",
                    "accountId": "123",
                    "amiId": "ami-1",
                    "instanceType": "t2.micro"
                  }
                }
              },
              {
                "instanceId": "a-2",
                "status": "UP",
                "hostName": "host-a-2",
                "app": "SERVICE-A",
                "ipAddr": "10.0.0.2",
                "dataCenterInfo": {
                  "name": "Amazon",
                  "metadata": {
                    "instanceId": "a-2",
                    "availabilityZone": "us-east-1b",
                    "accountId": "123",
                    "amiId": "ami-1",
                    "instanceType": "t2.micro"
                  }
                }
              }
            ]
          },
          {
            "name": "SERVICE-B",
            "instance": [
              {
                "instanceId": "b-1",
                "status": "DOWN",
                "hostName": "host-b-1",
                "app": "SERVICE-B",
                "ipAddr": "10.0.0.3",
                "dataCenterInfo": {
                  "name": "Amazon",
                  "metadata": {
                    "instanceId": "b-1",
                    "availabilityZone": "us-east-1a",
                    "accountId": "123",
                    "amiId": "ami-1",
                    "instanceType": "t2.micro"
                  }
                }
              }
            ]
          },
          {
            "name": "SERVICE-C",
            "instance": []
          }
        ]
      }
    }
    '''
    mockWebServer.enqueue(new MockResponse()
      .setBody(mockResponse)
      .addHeader("Content-Type", "application/json"))

    when: "calling loadEurekaApplications"
    def response = eurekaApi.loadEurekaApplications().execute()

    then: "complex structure is parsed correctly"
    response.isSuccessful()
    def apps = response.body()
    apps.applications.size() == 3
    apps.applications[0].instances.size() == 2
    apps.applications[1].instances.size() == 1
    apps.applications[2].instances.isEmpty()
  }
}
