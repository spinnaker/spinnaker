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

package com.netflix.spinnaker.orca.webhook.service

import com.netflix.spinnaker.kork.web.filters.ProvidedIdRequestFilterConfigurationProperties
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.config.UserConfiguredUrlRestrictions
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.webhook.config.WebhookProperties
import com.netflix.spinnaker.orca.webhook.config.WebhookConfiguration
import org.springframework.core.env.Environment
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.ResponseActions
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist

import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

class WebhookServiceSpec extends Specification {
  @Shared
  def webhookProperties = new WebhookProperties()

  @Shared
  def okHttpClientConfigurationProperties = new OkHttpClientConfigurationProperties()

  @Shared
  def webhookConfiguration = new WebhookConfiguration(webhookProperties)

  @Shared
  def userConfiguredUrlRestrictions = new UserConfiguredUrlRestrictions.Builder().withRejectLocalhost(false).withAllowedHostnamesRegex(".*").build()

  @Shared
  def requestFactory = webhookConfiguration.webhookRequestFactory(Mock(Environment),
    okHttpClientConfigurationProperties, userConfiguredUrlRestrictions,
    webhookProperties
  )

  @Shared
  def restTemplateProvider = new DefaultRestTemplateProvider(webhookConfiguration.restTemplate(requestFactory))

  def preconfiguredWebhookProperties = new WebhookProperties()

  def server = MockRestServiceServer.createServer(restTemplateProvider.restTemplate)

  def oortService = Mock(OortService)

  def providedIdRequestFilterConfigurationProperties = new ProvidedIdRequestFilterConfigurationProperties()

  @Subject
  def webhookService = new WebhookService(List.of(restTemplateProvider),
                                          userConfiguredUrlRestrictions,
                                          preconfiguredWebhookProperties,
                                          oortService, Optional.empty(),
                                          providedIdRequestFilterConfigurationProperties)

  @Unroll
  def "Webhook is being called with correct parameters"() {
    expect:
    ResponseActions responseActions = server.expect(requestTo("https://localhost/v1/test"))
      .andExpect(method(HttpMethod.POST))

    if(payload) {
      payload.each { k, v -> responseActions.andExpect(jsonPath('$.' + k).value(v)) }
    }
    if(customHeaders) {
      customHeaders.each { k, v -> responseActions.andExpect(header(k, v)) }
    }

    responseActions.andRespond(withSuccess('{"status": "SUCCESS"}', MediaType.APPLICATION_JSON))

    when:
    HttpHeaders headers = new HttpHeaders()
    headers.add("customHeader", "value")
    StageExecution stageExecution = new StageExecutionImpl(null, null, null, [
        'url': "https://localhost/v1/test",
        'method': HttpMethod.POST,
        'customHeaders': customHeaders,
        'payload': payload
    ])
    def responseEntity = webhookService.callWebhook(stageExecution)

    then:
    server.verify()
    responseEntity.statusCode == HttpStatus.OK
    responseEntity.body == ["status": "SUCCESS"]

    where:
    payload                                     | customHeaders
    ["payload1": "Hello", "payload2": "World!"] | ["X-HEADER": "value"]
    ["payload1": "Hello", "payload2": "World!"] | [:]
    [:]                                         | [:]
    null                                        | null

  }

  @Unroll
  def "Status endpoint is being called with headers #customHeaders"() {

    expect:
    def responseActions = server.expect(requestTo("https://localhost/v1/status/123"))
      .andExpect(method(HttpMethod.GET))

      if(customHeaders){
        customHeaders.each {k, v -> responseActions.andExpect(header(k, v))}
      }

      responseActions.andRespond(withSuccess('["element1", 123, false]', MediaType.APPLICATION_JSON))

    when:
    StageExecution stageExecution = new StageExecutionImpl(null, null, null, [
        'statusEndpoint': "https://localhost/v1/status/123",
        'method': HttpMethod.GET,
        'customHeaders': customHeaders,
        'payload': null
    ])
    def responseEntity = webhookService.getWebhookStatus(stageExecution)

    then:
    server.verify()
    responseEntity.statusCode == HttpStatus.OK
    responseEntity.body == ["element1", 123, false]

    where:
    customHeaders << [[Authorization: "Basic password"], [:], null]
  }

  def "Preconfigured webhooks should only include enabled webhooks"() {
    setup:
    def webhook1 = new WebhookProperties.PreconfiguredWebhook(label: "1", enabled: true)
    def webhook2 = new WebhookProperties.PreconfiguredWebhook(label: "2", enabled: false)
    def webhook3 = new WebhookProperties.PreconfiguredWebhook(label: "3", enabled: true)
    preconfiguredWebhookProperties.preconfigured << webhook1
    preconfiguredWebhookProperties.preconfigured << webhook2
    preconfiguredWebhookProperties.preconfigured << webhook3

    when:
    def preconfiguredWebhooks = webhookService.preconfiguredWebhooks

    then:
    preconfiguredWebhooks == [webhook1, webhook3]
  }

  def 'Should find an enabled preconfigured webhook by its type'() {
    given:
    def webhook1 = new WebhookProperties.PreconfiguredWebhook(label: "1", enabled: true, type: "type1")
    def webhook2 = new WebhookProperties.PreconfiguredWebhook(label: "2", enabled: false, type: "type2")
    def webhook2ButEnabled = new WebhookProperties.PreconfiguredWebhook(label: "2", enabled: true, type: "type2")
    def webhook3 = new WebhookProperties.PreconfiguredWebhook(label: "3", enabled: true, type: "type3")
    preconfiguredWebhookProperties.preconfigured << webhook1
    preconfiguredWebhookProperties.preconfigured << webhook2
    preconfiguredWebhookProperties.preconfigured << webhook2ButEnabled
    preconfiguredWebhookProperties.preconfigured << webhook3

    when:
    def maybeAPreconfiguredWebhook = webhookService.findPreconfiguredWebhook("type2")

    then:
    maybeAPreconfiguredWebhook.isPresent()
    maybeAPreconfiguredWebhook.get() == webhook2ButEnabled
  }

  def "Content-Type text/plain is turned into a string"() {
    expect:
    def responseActions = server.expect(requestTo("https://localhost/v1/text/test"))
      .andExpect(method(HttpMethod.GET))
    responseActions.andRespond(withSuccess('This is text/plain', MediaType.TEXT_PLAIN))

    when:
    StageExecution stageExecution = new StageExecutionImpl(null, null, null, [
        'url': "https://localhost/v1/text/test",
        'method': HttpMethod.GET,
        'customHeaders': null,
        'payload': null
    ])
    def responseEntity = webhookService.callWebhook(stageExecution)

    then:
    server.verify()
    responseEntity.statusCode == HttpStatus.OK
    responseEntity.body == 'This is text/plain'
  }

  def "Should accept PATCH request"() {
    setup:
    def responseActions = server.expect(requestTo("https://localhost/v1/test"))
      .andExpect(method(HttpMethod.PATCH))
    responseActions.andRespond(withSuccess())

    when:
    StageExecution stageExecution = new StageExecutionImpl(null, null, null, [
        'url': "https://localhost/v1/test",
        'method': HttpMethod.PATCH,
        'customHeaders': null,
        'payload': null
    ])
    def responseEntity = webhookService.callWebhook(stageExecution)

    then:
    noExceptionThrown()
    server.verify()
    responseEntity.statusCode == HttpStatus.OK
  }

  def 'Should read sensitive headers from preconfigured webhook config and include them in request headers'() {
    setup:
    def webhook = new WebhookProperties.PreconfiguredWebhook(label: "1", enabled: true, type: "type1",
        customHeaders: [
            // these headers should be ignored
            "Content-type": ["application/yaml"],
            "Accept": ["application/yaml"],
            "Other": ["value"]
        ],
        sensitiveHeaders: [
            // but this should be included
            "Authorization": ["Basic Zm9vOmJhcg=="]
        ])
    preconfiguredWebhookProperties.preconfigured << webhook
    def stageExecution = new StageExecutionImpl(null, "type1", null, [
        'type': "type1",
        'url': "https://localhost/v1/test",
        'method': HttpMethod.GET,
        'customHeaders': [
            "Content-type": ["application/json"],
            "Accept": ["application/json"]
        ],
    ])
    def responseActions = server.expect(requestTo("https://localhost/v1/test"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Content-type", "application/json"))
        .andExpect(header("Accept", "application/json"))
        .andExpect(header("Authorization", "Basic Zm9vOmJhcg=="))
        .andExpect(headerDoesNotExist("Other"))
    responseActions.andRespond(withSuccess())

    when:
    def responseEntity = webhookService.callWebhook(stageExecution)

    then:
    noExceptionThrown()
    server.verify()
    responseEntity.statusCode == HttpStatus.OK
  }

}
