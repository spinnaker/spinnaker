package com.netflix.spinnaker.echo.telemetry

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.util.JsonFormat
import com.netflix.spinnaker.echo.config.TelemetryConfig
import com.netflix.spinnaker.echo.api.events.Event
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper
import com.netflix.spinnaker.kork.proto.stats.Application
import com.netflix.spinnaker.kork.proto.stats.CloudProvider
import com.netflix.spinnaker.kork.proto.stats.Execution
import com.netflix.spinnaker.kork.proto.stats.DeploymentMethod
import com.netflix.spinnaker.kork.proto.stats.SpinnakerInstance
import com.netflix.spinnaker.kork.proto.stats.Stage
import com.netflix.spinnaker.kork.proto.stats.Status
import com.netflix.spinnaker.kork.proto.stats.Event as EventProto
import groovy.json.JsonOutput
import groovy.util.logging.Slf4j
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import retrofit.RetrofitError
import retrofit.client.Response
import spock.lang.Specification
import spock.lang.Subject

@Slf4j
class TelemetryEventListenerSpec extends Specification {

  def service = Mock(TelemetryService)
  def registry = CircuitBreakerRegistry.ofDefaults()
  def circuitBreaker = registry.circuitBreaker(TelemetryEventListener.TELEMETRY_REGISTRY_NAME)

  def instanceId = "test-instance"
  def instanceHash = "b6a8ed497aba799fb0033fcb3588de65e198a0d9b731c5481499251177074a8f"
  def spinnakerVersion = "1.2.3"

  def applicationName = "someApp"
  def applicationHash = "f0291bf122b40a43cb2129378272f205200dff0445af506346b1dc47127e258d"

  def executionId = "execution_id"
  def executionHash = "6d6de5b8d67c11fff6d817ea3e1190bc63857de0329d253b21aef6e5c6bbebf9"

  Event validEvent = mapToEventViaJson(
    details: [
      type       : "orca:pipeline:complete",
      application: applicationName,
    ],
    content: [
      spinnakerInstance : [
        id              : instanceHash,
        version         : spinnakerVersion,
        deploymentMethod: [
          type   : "kubernetes_operator",
          version: "v3.0.0"
        ]
      ],
      execution: [
        id     : executionId,
        type   : "PIPELINE",
        status : "SUCCEEDED",
        trigger: [
          type: "GIT"
        ],
        source: null,
        stages : [
          [
            type               : "deploy",
            status             : "succeeded", // lowercase testing
            syntheticStageOwner: null,
            context            : [
              "cloudProvider": "gce"
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

  Event appCreatedEvent = mapToEventViaJson(
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
          type: "MANUAL"
        ],
        stages : [
          [
            type               : "createApplication",
            status             : "succeeded", // lowercase testing
            syntheticStageOwner: null,
            context            : [
              "newState": [
                "name": applicationName,
                "cloudProviders": "gce,kubernetes",
              ],
            ]
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
      assert s.id == instanceHash
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
      assert stage1.cloudProvider.id == CloudProvider.ID.GCE

      Stage stage2 = stages.get(1)
      assert stage2.type == "wait"
      assert stage2.status == Status.TERMINAL

      return new Response("url", 200, "", [], null)
    }
  }

  def "test stat service in bad state"() {
    given:
    def configProps = new TelemetryConfig.TelemetryConfigProps()
      .setInstanceId(instanceId)
      .setSpinnakerVersion(spinnakerVersion)


    @Subject
    def listener = new TelemetryEventListener(service, configProps, registry)

    when:
    listener.processEvent(validEvent)

    then:
    1 * service.log(_) >> { _ ->
      throw RetrofitError.networkError("url", new IOException("Uh oh - Network error!"))
    }
    noExceptionThrown()

    when:
    listener.processEvent(validEvent)

    then:
    1 * service.log(_) >> { _ ->
      throw RetrofitError.httpError(
        "url",
        new Response("url", 500, "Uh oh - HTTP error!", [], null),
        null,
        null)
    }
    noExceptionThrown()

    when:
    listener.processEvent(validEvent)

    then:
    1 * service.log(_) >> { _ ->
      throw RetrofitError.unexpectedError("url", new Exception("Uh oh - Unexpected error!"))
    }
    noExceptionThrown()

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

  def "test create app cloud provider detection"() {
    given:
    def configProps = new TelemetryConfig.TelemetryConfigProps()
      .setInstanceId(instanceId)
      .setSpinnakerVersion(spinnakerVersion)

    @Subject
    def listener = new TelemetryEventListener(service, configProps, registry)

    when:
    listener.processEvent(appCreatedEvent)

    then:
    1 * service.log(_) >> { List args ->
      String body = args[0]?.toString()
      assert body != null

      EventProto.Builder eventBuilder = EventProto.newBuilder()
      JsonFormat.parser().merge(body, eventBuilder)

      EventProto e = eventBuilder.build()

      Execution ex = e.getExecution()
      List<Stage> stages = ex.getStagesList()
      assert stages != null
      assert stages.size() == 2

      Stage stage1 = stages.get(0)
      assert stage1.type == "createApplication"
      assert stage1.status == Status.SUCCEEDED
      assert stage1.cloudProvider.id == CloudProvider.ID.GCE

      Stage stage2 = stages.get(1)
      assert stage2.type == "createApplication"
      assert stage2.status == Status.SUCCEEDED
      assert stage2.cloudProvider.id == CloudProvider.ID.KUBERNETES
    }
  }

  def "test event has deploy method information happy path"(String deploymentType, String deployVersion) {
      given:
      def deployMethod = new TelemetryConfig.TelemetryConfigProps.DeploymentMethod()
        .setType(deploymentType)
        .setVersion(deployVersion)

      def configProps = new TelemetryConfig.TelemetryConfigProps()
        .setInstanceId(instanceId)
        .setSpinnakerVersion(spinnakerVersion)
        .setDeploymentMethod(deployMethod)

      def deployEnumType = DeploymentMethod.Type.valueOf(deploymentType.toUpperCase())

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
        assert s.deploymentMethod != null
        assert s.deploymentMethod.type == deployEnumType
        assert s.deploymentMethod.version == deployVersion
      }

      where:
      deploymentType        | deployVersion
      "none"                | ""
      "other"               | "3.2.1"
      "halyard"             | "1.0.0"
      "kubernetes_operator" | "0.3.0"
  }

  def "test event, with null deploy method info"() {
    given:
    def configProps = new TelemetryConfig.TelemetryConfigProps()
      .setInstanceId(instanceId)
      .setSpinnakerVersion(spinnakerVersion)
      .setDeploymentMethod(new TelemetryConfig.TelemetryConfigProps.DeploymentMethod())

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
      assert s.deploymentMethod.type == DeploymentMethod.Type.NONE
      assert s.deploymentMethod.version == ""
    }
  }

  // This function more closely mimics Jackson's deserialization of JSON from an
  // incoming HTTP request.
  private static Event mapToEventViaJson(Object o) {
    def json = JsonOutput.toJson(o)
    return EchoObjectMapper.getInstance().readValue(json, Event)
  }
}
