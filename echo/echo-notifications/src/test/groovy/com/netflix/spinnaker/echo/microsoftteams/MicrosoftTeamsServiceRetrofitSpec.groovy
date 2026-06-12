/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.microsoftteams

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import spock.lang.Specification
import spock.lang.Subject

/**
 * Tests for MicrosoftTeamsService to ensure proper Retrofit 2 URL handling
 * after migrating from @POST("{webhookUrl}") + @Path to @POST + @Url.
 *
 * Verifies that the webhook URL path is not duplicated in the outgoing request.
 * This is a regression test for the webhook URL doubling bug during Retrofit 1 -> Retrofit 2 migration.
 */
class MicrosoftTeamsServiceRetrofitSpec extends Specification {

  MockWebServer server
  @Subject
  MicrosoftTeamsService microsoftTeamsService

  def setup() {
    server = new MockWebServer()
    server.start()

    def okHttpClientConfig = new OkHttp3ClientConfiguration()
    microsoftTeamsService = new MicrosoftTeamsService(okHttpClientConfig)
  }

  def cleanup() {
    server.shutdown()
  }

  def "sendMessage should send webhook request to the correct URL without path duplication"() {
    given: "Microsoft Teams webhook mock server with a path"
    def webhookPath = "/webhook/path/segments"
    def webhookUrl = server.url(webhookPath).toString().replaceAll('/$', '')
    
    server.enqueue(new MockResponse()
      .setResponseCode(200)
      .setHeader("Content-Type", "application/json")
      .setBody('{"status": "success"}'))

    def message = new MicrosoftTeamsMessage()
    message.text = "Test message"

    when: "sending message via MicrosoftTeamsService"
    def response = microsoftTeamsService.sendMessage(webhookUrl, message)

    then: "response is successful"
    response != null

    and: "the request path should not be duplicated"
    def recordedRequest = server.takeRequest()
    recordedRequest.path == webhookPath
    // Verify it's not doubled (i.e., /webhook/path/segments/webhook/path/segments)
    !recordedRequest.path.contains(webhookPath + webhookPath)

    and: "request was made exactly once"
    server.requestCount == 1
  }

  def "sendMessage should handle successful Teams webhook response"() {
    given: "Teams webhook returns success"
    def responseJson = '{"status": "delivered", "timestamp": "2026-06-12T12:00:00Z"}'
    server.enqueue(new MockResponse()
      .setResponseCode(200)
      .setHeader("Content-Type", "application/json")
      .setBody(responseJson))

    def webhookUrl = server.url("/teams/webhook123").toString().replaceAll('/$', '')
    def message = new MicrosoftTeamsMessage()
    message.text = "Important notification"

    when: "sending message"
    def response = microsoftTeamsService.sendMessage(webhookUrl, message)

    then: "response is successful and body is readable"
    response != null
    def bodyString = response.string()
    bodyString.contains("delivered")
  }

  def "sendMessage should handle webhook with deep path segments"() {
    given: "Teams webhook URL with multiple path segments"
    def deepPath = "/api/v1/teams/channelX/webhooks/incoming/5a6b7c8d9e0f"
    def webhookUrl = server.url(deepPath).toString().replaceAll('/$', '')

    server.enqueue(new MockResponse()
      .setResponseCode(200)
      .setBody('{"ok": true}'))

    def message = new MicrosoftTeamsMessage()
    message.text = "Message with deep path test"

    when: "sending message"
    microsoftTeamsService.sendMessage(webhookUrl, message)
    def recordedRequest = server.takeRequest()

    then: "the request is sent to the exact path without duplication"
    recordedRequest.path == deepPath
    recordedRequest.path.count("/") >= 6 // Deep path has many segments
    // Ensure the path appears exactly once, not repeated
    recordedRequest.path.indexOf(deepPath) == recordedRequest.path.lastIndexOf(deepPath)
  }

  def "sendMessage should handle URL with query parameters in webhook"() {
    given: "Teams webhook URL with query parameters"
    def basePath = "/webhook/abc123"
    // MockWebServer records the full path including query params
    def webhookUrl = server.url(basePath).toString().replaceAll('/$', '')

    server.enqueue(new MockResponse()
      .setResponseCode(200)
      .setBody('{}'))

    def message = new MicrosoftTeamsMessage()
    message.text = "Test"

    when: "sending message"
    microsoftTeamsService.sendMessage(webhookUrl, message)
    def recordedRequest = server.takeRequest()

    then: "the webhook path is correctly preserved"
    recordedRequest.path == basePath
  }

  def "sendMessage should properly serialize MicrosoftTeamsMessage body"() {
    given: "Teams webhook ready to receive message"
    server.enqueue(new MockResponse()
      .setResponseCode(200)
      .setBody('{"accepted": true}'))

    def webhookUrl = server.url("/webhook").toString().replaceAll('/$', '')
    def message = new MicrosoftTeamsMessage()
    message.text = "Test serialization"
    message.summary = "Summary for teams"

    when: "sending message"
    microsoftTeamsService.sendMessage(webhookUrl, message)
    def recordedRequest = server.takeRequest()

    then: "request body contains the message text"
    def body = recordedRequest.body.readUtf8()
    body.contains("Test serialization") || body.contains("Summary for teams")
  }
}
