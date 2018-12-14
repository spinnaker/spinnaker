package com.netflix.spinnaker.echo.slack

import com.netflix.spinnaker.echo.config.SlackConfig
import groovy.json.JsonSlurper
import org.apache.http.HttpEntity
import org.apache.http.NameValuePair
import org.apache.http.client.utils.URLEncodedUtils
import retrofit.client.Client
import retrofit.client.Request
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import retrofit.mime.TypedOutput
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import java.nio.charset.Charset

import static java.util.Collections.emptyList
import static retrofit.Endpoints.newFixedEndpoint
import static retrofit.RestAdapter.LogLevel

class SlackServiceSpec extends Specification {
  @Subject slackConfig = new SlackConfig()
  @Subject mockHttpClient
  @Subject BlockingVariable<String> actualUrl
  @Subject BlockingVariable<String> actualPayload

  def setup() {
    actualUrl = new BlockingVariable<String>()
    actualPayload = new BlockingVariable<String>()

    mockHttpClient = Mock(Client)
    // intercepting the HTTP call
    mockHttpClient.execute(*_) >> { Request request ->
      actualUrl.set(request.url)
      actualPayload.set(getString(request.body))
      mockResponse()
    }
  }

  def 'test sending Slack notification using incoming web hook'() {

    given: "a SlackService configured to send using a mocked HTTP client and useIncomingHook=true"
    def useIncomingHook = true
    def endpoint = newFixedEndpoint(SlackConfig.SLACK_INCOMING_WEBHOOK)

    def slackService = slackConfig.slackService(useIncomingHook, endpoint, mockHttpClient, LogLevel.FULL)

    when: "sending a notification"
    slackService.sendMessage(token, new SlackAttachment("Title", "the text"), "#testing", true)
    def responseJson = new JsonSlurper().parseText(actualPayload.get())

    then: "the HTTP URL and payload intercepted are the ones expected"
    actualUrl.get() == expectedUrl
    responseJson.attachments[0]["title"] == "Title"
    responseJson.attachments[0]["text"] == "the text"
    responseJson.attachments[0]["fallback"] == "the text"
    responseJson.attachments[0]["footer"] == "Spinnaker"
    responseJson.attachments[0]["mrkdwn_in"] == ["text"]
    responseJson.channel == "#testing"

    where:
    token            | expectedUrl
    "NEW/TYPE/TOKEN" | "https://hooks.slack.com/services/NEW/TYPE/TOKEN"
  }


  def 'test sending Slack notification using chat.postMessage API'() {

    given: "a SlackService configured to send using a mocked HTTP client and useIncomingHook=false"
    def useIncomingHook = false
    def endpoint = newFixedEndpoint(SlackConfig.SLACK_CHAT_API)

    def slackService = slackConfig.slackService(useIncomingHook, endpoint, mockHttpClient, LogLevel.FULL)

    when: "sending a notification"
    slackService.sendMessage(token, new SlackAttachment("Title", "the text"), "#testing", true)

    // Parse URL Encoded Form
    def params = URLEncodedUtils.parse(actualPayload.get(), Charset.forName("UTF-8"))
    def attachmentsField = getField(params, "attachments")
    def attachmentsJson = parseJson(attachmentsField.value)

    def channelField = getField(params, "channel")
    def asUserField = getField(params, "as_user")

    then: "the HTTP URL and payload intercepted are the ones expected"
    actualUrl.get() == expectedUrl
    getField(params, "token").value == "oldStyleToken"
    attachmentsJson[0]["title"] == "Title"
    attachmentsJson[0]["text"] == "the text"
    attachmentsJson[0]["fallback"] == "the text"
    attachmentsJson[0]["footer"] == "Spinnaker"
    attachmentsJson[0]["mrkdwn_in"] == ["text"]
    channelField.value == "#testing"
    asUserField.value == "true"

    where:
    token           | expectedUrl
    "oldStyleToken" | "https://slack.com/api/chat.postMessage"
  }

  def static getField(Collection<NameValuePair> params, String fieldName) {
    params.find({ it -> it.name == fieldName })
  }

  def static parseJson(String value) {
    new JsonSlurper().parseText(value)
  }

  static Response mockResponse() {
    new Response("url", 200, "nothing", emptyList(), new TypedByteArray("application/json", "response".bytes))
  }

  static String getString(TypedOutput typedOutput) {
    OutputStream os = new ByteArrayOutputStream()
    typedOutput.writeTo(os)
    new String(os.toByteArray(), "UTF-8")
  }
}
