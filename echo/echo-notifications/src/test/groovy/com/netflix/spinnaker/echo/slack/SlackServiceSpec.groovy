package com.netflix.spinnaker.echo.slack

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.http.RequestListener
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration
import com.netflix.spinnaker.echo.api.Notification
import com.netflix.spinnaker.echo.test.config.Retrofit2BasicLogTestConfig
import com.netflix.spinnaker.echo.test.config.Retrofit2TestConfig
import com.netflix.spinnaker.echo.config.SlackAppProperties
import com.netflix.spinnaker.echo.config.SlackConfig
import com.netflix.spinnaker.echo.config.SlackLegacyProperties
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import groovy.json.JsonSlurper

import java.nio.charset.Charset
import org.apache.http.NameValuePair
import org.apache.http.client.utils.URLEncodedUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.RequestEntity
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

@SpringBootTest(classes = [Retrofit2TestConfig, Retrofit2BasicLogTestConfig],
  properties = ["slack.enabled=true"],
  webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SlackServiceSpec extends Specification {
  @Subject slackConfig = new SlackConfig()
  @Subject BlockingVariable<String> actualUrl
  @Subject BlockingVariable<String> actualPayload
  SlackLegacyProperties configProperties
  SlackAppProperties appProperties
  WireMockServer wireMockServer

  @Autowired
  OkHttp3ClientConfiguration okHttpClientConfig

  def setup() {
    actualUrl = new BlockingVariable<String>(5)
    actualPayload = new BlockingVariable<String>(5)

    configProperties = new SlackLegacyProperties()
    appProperties = new SlackAppProperties()

    wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    wireMockServer.addMockServiceRequestListener(new RequestListener() {

      @Override
      void requestReceived(com.github.tomakehurst.wiremock.http.Request request, com.github.tomakehurst.wiremock.http.Response response) {
        actualUrl.set(request.absoluteUrl)
        actualPayload.set(request.bodyAsString)
      }
    });

    wireMockServer.start();
    configProperties.baseUrl = wireMockServer.baseUrl()

    wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo("/NEW/TYPE/TOKEN"))
      .willReturn(WireMock.aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody("{\"message\": \"response\", \"code\": 200}")))

    wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo("/api/chat.postMessage"))
      .willReturn(WireMock.aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody("{\"message\": \"response\", \"code\": 200}")))
  }

  def cleanup(){
    wireMockServer.stop()
  }

  def 'test sending Slack notification using incoming web hook'() {

    given: "a SlackService configured to send using a mocked response and useIncomingHook=true"
    configProperties.forceUseIncomingWebhook = true
    configProperties.token = "NEW/TYPE/TOKEN"

    def expectedUrl = wireMockServer.baseUrl() + "/NEW/TYPE/TOKEN"

    def slackService = slackConfig.slackService(configProperties, okHttpClientConfig)

    when: "sending a notification"
    slackService.sendMessage(new SlackAttachment("Title", "the text"), "#testing", true)
    def responseJson = new JsonSlurper().parseText(actualPayload.get())

    then: "the HTTP URL and payload intercepted are the ones expected"
    actualUrl.get() == expectedUrl
    responseJson.attachments[0]["title"] == "Title"
    responseJson.attachments[0]["text"] == "the text"
    responseJson.attachments[0]["fallback"] == "the text"
    responseJson.attachments[0]["footer"] == "Spinnaker"
    responseJson.attachments[0]["mrkdwn_in"] == ["text"]
    responseJson.channel == "#testing"

  }


  def 'test sending Slack notification using chat.postMessage API'() {

    given: "a SlackService configured to send using a mocked HTTP client and useIncomingHook=false"
    configProperties.forceUseIncomingWebhook = false
    configProperties.token = "oldStyleToken"

    def expectedUrl = wireMockServer.baseUrl() + "/api/chat.postMessage"

    def slackService = slackConfig.slackService(configProperties, okHttpClientConfig)

    when: "sending a notification"
    slackService.sendMessage(new SlackAttachment("Title", "the text"), "#testing", true)

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
  }

  def 'sending an interactive Slack notification'() {

    given: "a SlackService configured to send a message with the chat API"
    configProperties.forceUseIncomingWebhook = false
    configProperties.token = "shhh"

    def slackService = slackConfig.slackService(configProperties, okHttpClientConfig)

    when: "sending a notification with interactive actions"
    slackService.sendMessage(
      new SlackAttachment(
        "Title",
        "the text",
        new Notification.InteractiveActions(callbackServiceId: "test", callbackMessageId: "blah", actions: [
          new Notification.ButtonAction(name: "choice", label: "OK", value: "ok")
        ])
      ),
      "#testing", true)

    // Parse URL Encoded Form
    def params = URLEncodedUtils.parse(actualPayload.get(), Charset.forName("UTF-8"))
    def attachmentsField = getField(params, "attachments")
    def attachmentsJson = parseJson(attachmentsField.value)

    then: "a Slack attachment payload is generated as expected"
    attachmentsJson[0]["title"] == "Title"
    attachmentsJson[0]["text"] == "the text"
    attachmentsJson[0]["fallback"] == "the text"
    attachmentsJson[0]["footer"] == "Spinnaker"
    attachmentsJson[0]["mrkdwn_in"] == ["text"]
    attachmentsJson[0]["actions"].size() == 1
    attachmentsJson[0]["actions"][0] == [
      type: "button",
      name: "choice",
      text: "OK",
      value: "ok"
    ]
  }

  def "verifying a Slack notification callback"() {
    given: "a SlackAppService configured with a signing secret and an incoming callback"
    appProperties.signingSecret = "d41090bb6ec741bb9f68f4d77d34fa0ad897c5af"

    def slackAppService = slackConfig.slackAppService(appProperties, okHttpClientConfig)

    String timestamp = "1581528126"
    String payload = getClass().getResource("/slack/callbackRequestBody.txt").text
    String slackRequestBody = "payload=" + URLEncoder.encode(payload, "UTF-8")
    String signature = slackAppService.calculateSignature(timestamp, slackRequestBody)

    RequestEntity<String> request = new RequestEntity<>(
      slackRequestBody,
      new HttpHeaders(
        "X-Slack-Signature": signature,
        "X-Slack-Request-Timestamp": timestamp
      ),
      HttpMethod.POST,
      new URI("/notifications/callbacks"))

    when: "the verifySignature method is called"
    slackAppService.verifySignature(request, false)

    then: "the calculated signature matches the received signature"
    notThrown(InvalidRequestException)
  }


  def static getField(Collection<NameValuePair> params, String fieldName) {
    params.find({ it -> it.name == fieldName })
  }

  def static parseJson(String value) {
    new JsonSlurper().parseText(value)
  }
}
