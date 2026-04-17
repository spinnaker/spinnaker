/*
    Copyright (C) 2024 Nordix Foundation.
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

import com.netflix.spinnaker.echo.api.events.Event
import io.cloudevents.CloudEvent
import io.cloudevents.core.builder.CloudEventBuilder
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import spock.lang.Specification
import spock.lang.Subject

class CDEventsOTelSenderSpec extends Specification {

  static final String ENDPOINT = "http://localhost:4317"

  InMemorySpanExporter spanExporter = InMemorySpanExporter.create()

  @Subject
  CDEventsOTelSender sender

  def setup() {
    def config = new CDEventsConfigProperties()
    sender = new CDEventsOTelSender(config)

    // Inject a test SDK with in-memory exporter into the sdkCache
    def testSdk = OpenTelemetrySdk.builder()
      .setTracerProvider(
        SdkTracerProvider.builder()
          .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
          .build()
      ).build()

    def sdkCache = CDEventsOTelSender.getDeclaredField("sdkCache")
    sdkCache.setAccessible(true)
    ((Map) sdkCache.get(sender)).put(ENDPOINT, testSdk)
  }

  def "pipeline start span is created and traceparent is stored"() {
    when:
    sender.send(buildCloudEvent("dev.cdevents.pipelinerun.started.0.1.1", "exec-1"), ENDPOINT)

    then:
    def spans = spanExporter.finishedSpanItems
    spans.size() == 1
    spans[0].name == "dev.cdevents.pipelinerun.started.0.1.1"
    !spans[0].parentSpanContext.isValid()

    and: "traceparent is stored for the execution"
    def traceCtx = getTraceContextMap()
    traceCtx.containsKey("exec-1")
    traceCtx.get("exec-1") =~ /00-[0-9a-f]{32}-[0-9a-f]{16}-01/
  }

  def "stage span becomes child of pipeline root span"() {
    given:
    sender.send(buildCloudEvent("dev.cdevents.pipelinerun.started.0.1.1", "exec-2"), ENDPOINT)
    def rootTraceId = spanExporter.finishedSpanItems[0].traceId

    when:
    sender.send(buildCloudEvent("dev.cdevents.taskrun.started.0.1.1", "exec-2"), ENDPOINT)

    then:
    def spans = spanExporter.finishedSpanItems
    spans.size() == 2
    def stageSpan = spans[1]
    stageSpan.traceId == rootTraceId
    stageSpan.parentSpanContext.isValid()
  }

  def "pipeline finish cleans up stored trace context"() {
    given:
    sender.send(buildCloudEvent("dev.cdevents.pipelinerun.started.0.1.1", "exec-3"), ENDPOINT)

    when:
    sender.send(buildCloudEvent("dev.cdevents.pipelinerun.finished.0.1.1", "exec-3"), ENDPOINT)

    then:
    !getTraceContextMap().containsKey("exec-3")
  }

  def "stage name is appended to span name"() {
    given:
    def event = new Event(content: [
      context: [stageDetails: [name: "Build"]]
    ])

    when:
    sender.send(
      buildCloudEvent("dev.cdevents.taskrun.started.0.1.1", "exec-4"),
      ENDPOINT, [type: "stage"], event)

    then:
    def spans = spanExporter.finishedSpanItems
    spans[0].name == "dev.cdevents.taskrun.started.Build"
    spans[0].attributes.get(AttributeKey.stringKey("cdevents.stage.name")) == "Build"
  }

  def "customData entries are promoted to span attributes"() {
    when:
    sender.send(buildCloudEventWithCustomData("dev.cdevents.pipelinerun.started.0.1.1", "exec-5",
      [commitSha: "abc123", repo: "my-service"]), ENDPOINT)

    then:
    def attrs = spanExporter.finishedSpanItems[0].attributes
    attrs.get(AttributeKey.stringKey("cdevents.custom.commitSha")) == "abc123"
    attrs.get(AttributeKey.stringKey("cdevents.custom.repo")) == "my-service"
  }

  def "finished span with failure outcome gets ERROR status"() {
    when:
    sender.send(buildCloudEventWithOutcome("dev.cdevents.pipelinerun.finished.0.1.1", "exec-6", "failure"), ENDPOINT)

    then:
    spanExporter.finishedSpanItems[0].status.statusCode == StatusCode.ERROR
  }

  def "finished span with success outcome does not get ERROR status"() {
    when:
    sender.send(buildCloudEventWithOutcome("dev.cdevents.pipelinerun.finished.0.1.1", "exec-7", "success"), ENDPOINT)

    then:
    spanExporter.finishedSpanItems[0].status.statusCode != StatusCode.ERROR
  }

  def "span without execution ID does not store trace context"() {
    when:
    sender.send(buildCloudEvent("dev.cdevents.pipelinerun.started.0.1.1", null), ENDPOINT)

    then:
    spanExporter.finishedSpanItems.size() == 1
    getTraceContextMap().isEmpty()
  }

  // --- helpers ---

  private Map<String, String> getTraceContextMap() {
    def field = CDEventsOTelSender.getDeclaredField("pipelineTraceContext")
    field.setAccessible(true)
    return (Map<String, String>) field.get(sender)
  }

  private static CloudEvent buildCloudEvent(String type, String executionId) {
    def data = executionId != null
      ? """{"subject":{"id":"${executionId}"}}"""
      : """{"subject":{}}"""
    CloudEventBuilder.v1()
      .withId(UUID.randomUUID().toString())
      .withSource(URI.create("http://spinnaker"))
      .withType(type)
      .withData("application/json", data.bytes)
      .build()
  }

  private static CloudEvent buildCloudEventWithCustomData(String type, String executionId, Map customData) {
    def mapper = new groovy.json.JsonBuilder([subject: [id: executionId], customData: customData])
    CloudEventBuilder.v1()
      .withId(UUID.randomUUID().toString())
      .withSource(URI.create("http://spinnaker"))
      .withType(type)
      .withData("application/json", mapper.toString().bytes)
      .build()
  }

  private static CloudEvent buildCloudEventWithOutcome(String type, String executionId, String outcome) {
    def data = """{"subject":{"id":"${executionId}"},"outcome":"${outcome}"}"""
    CloudEventBuilder.v1()
      .withId(UUID.randomUUID().toString())
      .withSource(URI.create("http://spinnaker"))
      .withType(type)
      .withData("application/json", data.bytes)
      .build()
  }
}
