package com.netflix.spinnaker.echo.telemetry

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.util.JsonFormat
import com.netflix.spinnaker.echo.config.TelemetryConfig
import com.netflix.spinnaker.echo.model.Event
import com.netflix.spinnaker.kork.proto.stats.*
import com.netflix.spinnaker.kork.proto.stats.Event as EventProto
import groovy.json.JsonOutput
import groovy.util.logging.Slf4j
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import retrofit.client.Response
import spock.lang.Specification
import spock.lang.Subject

@Slf4j
class TelemetryEventListenerSpec extends Specification {

  def service = Mock(TelemetryService)
  def registry = CircuitBreakerRegistry.ofDefaults()
  def circuitBreaker = registry.circuitBreaker(TelemetryEventListener.TELEMETRY_REGISTRY_NAME)

  def instanceId = "test-instance"
  def spinnakerVersion = "1.2.3"

  def applicationName = "someApp"
  def applicationHash = "e40464bf6d04933c6011c29974eb328777669813a76583e5d547941427df686f"

  def executionId = "execution_id"
  def executionHash = "6d6de5b8d67c11fff6d817ea3e1190bc63857de0329d253b21aef6e5c6bbebf9"

  Event validEvent = mapToEventViaJson(
    details: [
      type       : "orca:pipeline:complete",
      application: applicationName,
    ],
    content: [
      execution: [
        id     : executionId,
        type   : "PIPELINE",
        status : "SUCCEEDED",
        trigger: [
          type: "GIT"
        ],
        stages : [
          [
            type               : "deploy",
            status             : "succeeded", // lowercase testing
            syntheticStageOwner: null,
            context            : [
              "cloudProvider": "nine"
            ]
          ],
          [
            type               : "removed",
            syntheticStageOwner: "somethingNonNull",
            status             : "SUCCEEDED"
          ],
          [
            type  : "wait",
            status: "TERMINAL"
          ],
        ]
      ]
    ]
  )

  def setup() {
    circuitBreaker.reset()
  }

  def "test Event validation"() {
    given:
    def configProps = new TelemetryConfig.TelemetryConfigProps()

    @Subject
    def listener = new TelemetryEventListener(service, configProps, registry)

    when: "null details"
    listener.processEvent(new Event())

    then:
    0 * service.log(_)
    noExceptionThrown()

    when: "null content"
    listener.processEvent(mapToEventViaJson(
      details: [:]
    ))

    then:
    0 * service.log(_)
    noExceptionThrown()

    when: "irrelevant details type are ignored"
    listener.processEvent(mapToEventViaJson(
      details: [
        type: "foobar1",
      ],
      content: [:]
    ))

    then:
    0 * service.log(_)
    noExceptionThrown()

    when: "missing application ID"
    listener.processEvent(mapToEventViaJson(
      details: [
        type: "orca:orchestration:complete",
      ],
      content: [:]
    ))

    then:
    0 * service.log(_)
    noExceptionThrown()
  }

  def "send a telemetry event"() {
    given:
    def configProps = new TelemetryConfig.TelemetryConfigProps()
      .setInstanceId(instanceId)
      .setSpinnakerVersion(spinnakerVersion)

    @Subject
    def listener = new TelemetryEventListener(service, configProps, registry)

    when:
    listener.processEvent(validEvent)

    then:
    1 * service.log(_) >> { List args ->
      String body = args[0]?.toString()
      assert body != null

      // Note the handy Groovy feature of import aliasing Event->EventProto
      EventProto.Builder eventBuilder = EventProto.newBuilder()
      JsonFormat.parser().merge(body, eventBuilder)

      EventProto e = eventBuilder.build()
      assert e != null

      SpinnakerInstance s = e.spinnakerInstance
      assert s != null
      assert s.id == instanceId
      assert s.version == spinnakerVersion

      Application a = e.getApplication()
      assert a != null
      assert a.id == applicationHash

      Execution ex = e.getExecution()
      assert ex != null
      assert ex.id == executionHash
      assert ex.type == Execution.Type.PIPELINE
      assert ex.trigger.type == Execution.Trigger.Type.GIT

      List<Stage> stages = ex.getStagesList()
      assert stages != null
      assert stages.size() == 2

      Stage stage1 = stages.get(0)
      assert stage1.type == "deploy"
      assert stage1.status == Status.SUCCEEDED
      assert stage1.cloudProvider.id == "nine"

      Stage stage2 = stages.get(1)
      assert stage2.type == "wait"
      assert stage2.status == Status.TERMINAL

      return new Response("url", 200, "", [], null)
    }
  }

  def "test circuit breaker"() {
    given:
    def configProps = new TelemetryConfig.TelemetryConfigProps()
      .setInstanceId(instanceId)
      .setSpinnakerVersion(spinnakerVersion)

    @Subject
    def listener = new TelemetryEventListener(service, configProps, registry)

    circuitBreaker.transitionToOpenState()
    boolean eventSendAttempted = false
    circuitBreaker.getEventPublisher().onCallNotPermitted { event ->
      log.debug("Event send attempted, and blocked by open circuit breaker.")
      eventSendAttempted = true
    }

    when:
    listener.processEvent(validEvent)

    then:
    eventSendAttempted
  }

  def "test bogus enums"() {
    given:
    def configProps = new TelemetryConfig.TelemetryConfigProps()
      .setInstanceId(instanceId)
      .setSpinnakerVersion(spinnakerVersion)

    @Subject
    def listener = new TelemetryEventListener(service, configProps, registry)

    when: "bogus enums"
    listener.processEvent(mapToEventViaJson(
      details: [
        type       : "orca:orchestration:complete",
        application: "foobar",
      ],
      content: [
        execution: [
          status : "bogusExecStatus",
          type   : "bogusType",
          trigger: [:], // missing type
          stages : [
            [
              type  : "myType",
              status: "bogusStageStatus"
            ]
          ]
        ],
      ],
    ))

    then:
    1 * service.log(_) >> { List args ->
      String body = args[0]?.toString()
      assert body != null

      // Note the handy Groovy feature of import aliasing Event->EventProto
      EventProto.Builder eventBuilder = EventProto.newBuilder()
      JsonFormat.parser().merge(body, eventBuilder)
      EventProto e = eventBuilder.build()
      assert e != null

      Execution ex = e.getExecution()
      assert ex.status == Status.UNKNOWN
      assert ex.type == Execution.Type.UNKNOWN

      Execution.Trigger t = ex.getTrigger()
      assert t != null
      assert t.type == Execution.Trigger.Type.UNKNOWN

      Stage s = ex.getStages(0)
      assert s.type == "myType"
      assert s.status == Status.UNKNOWN

    }
  }

  // This function more closely mimics Jackson's deserialization of JSON from an
  // incoming HTTP request.
  private static Event mapToEventViaJson(Object o) {
    def json = JsonOutput.toJson(o)
    return new ObjectMapper().readValue(json, Event)
  }
}
