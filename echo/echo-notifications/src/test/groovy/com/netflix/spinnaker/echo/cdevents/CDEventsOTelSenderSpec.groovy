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

import com.fasterxml.jackson.databind.ObjectMapper
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
    def objectMapper = new ObjectMapper()
    sender = new CDEventsOTelSender(config, objectMapper)

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

  def "pipeline start span has deterministic trace ID from execution ID"() {
    when:
    sender.send(buildCloudEvent("dev.cdevents.pipelinerun.started.0.1.1", "exec-1"), ENDPOINT)

    then:
    def spans = spanExporter.finishedSpanItems
    spans.size() == 1
    spans[0].name == "dev.cdevents.pipelinerun.started"
    spans[0].traceId == CDEventsOTelSender.traceIdFromExecutionId("exec-1")
    spans[0].parentSpanId == CDEventsOTelSender.rootSpanIdFromExecutionId("exec-1")
  }

  def "stage span becomes child of deterministic root span"() {
    given:
    def expectedTraceId = CDEventsOTelSender.traceIdFromExecutionId("exec-2")
    def expectedParentSpanId = CDEventsOTelSender.rootSpanIdFromExecutionId("exec-2")

    when:
    sender.send(buildCloudEvent("dev.cdevents.taskrun.started.0.1.1", "exec-2"), ENDPOINT)

    then:
    def spans = spanExporter.finishedSpanItems
    spans.size() == 1
    def stageSpan = spans[0]
    stageSpan.traceId == expectedTraceId
    stageSpan.parentSpanId == expectedParentSpanId
    stageSpan.parentSpanContext.isValid()
  }

  def "multiple pods produce same trace hierarchy without shared state"() {
    given: "two independent sender instances (simulating two pods)"
    def config = new CDEventsConfigProperties()
    def objectMapper = new ObjectMapper()
    def sender2 = new CDEventsOTelSender(config, objectMapper)
    def exporter2 = InMemorySpanExporter.create()
    def testSdk2 = OpenTelemetrySdk.builder()
      .setTracerProvider(
        SdkTracerProvider.builder()
          .addSpanProcessor(SimpleSpanProcessor.create(exporter2))
          .build()
      ).build()
    def sdkCache = CDEventsOTelSender.getDeclaredField("sdkCache")
    sdkCache.setAccessible(true)
    ((Map) sdkCache.get(sender2)).put(ENDPOINT, testSdk2)

    def expectedTraceId = CDEventsOTelSender.traceIdFromExecutionId("exec-mp")
    def expectedParentSpanId = CDEventsOTelSender.rootSpanIdFromExecutionId("exec-mp")

    when: "pod 1 handles pipeline start"
    sender.send(buildCloudEvent("dev.cdevents.pipelinerun.started.0.1.1", "exec-mp"), ENDPOINT)

    and: "pod 2 handles a stage event"
    sender2.send(buildCloudEvent("dev.cdevents.taskrun.started.0.1.1", "exec-mp"), ENDPOINT)

    then: "both produce spans in the same trace with same parent"
    def rootSpan = spanExporter.finishedSpanItems[0]
    def childSpan = exporter2.finishedSpanItems[0]
    rootSpan.traceId == expectedTraceId
    childSpan.traceId == expectedTraceId
    rootSpan.parentSpanId == expectedParentSpanId
    childSpan.parentSpanId == expectedParentSpanId
  }

  def "pipelinerun.queued is treated as a root span with deterministic trace ID"() {
    when:
    sender.send(buildCloudEvent("dev.cdevents.pipelinerun.queued.0.1.1", "exec-q"), ENDPOINT)

    then:
    def spans = spanExporter.finishedSpanItems
    spans.size() == 1
    spans[0].name == "dev.cdevents.pipelinerun.queued"
    spans[0].traceId == CDEventsOTelSender.traceIdFromExecutionId("exec-q")
    spans[0].parentSpanId == CDEventsOTelSender.rootSpanIdFromExecutionId("exec-q")
  }

  def "stage name is shown in brackets in span name"() {
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
    spans[0].name == "dev.cdevents.taskrun.started [Build]"
    spans[0].attributes.get(AttributeKey.stringKey("cdevents.stage.name")) == "Build"
  }

  def "cdevents.type attribute uses short type name"() {
    when:
    sender.send(buildCloudEvent("dev.cdevents.pipelinerun.started.0.1.1", "exec-t"), ENDPOINT)

    then:
    def attrs = spanExporter.finishedSpanItems[0].attributes
    attrs.get(AttributeKey.stringKey("cdevents.type")) == "dev.cdevents.pipelinerun.started"
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

  def "span without execution ID has no parent"() {
    when:
    sender.send(buildCloudEvent("dev.cdevents.taskrun.started.0.1.1", null), ENDPOINT)

    then:
    def spans = spanExporter.finishedSpanItems
    spans.size() == 1
    !spans[0].parentSpanContext.isValid()
  }

  // --- helpers ---

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
