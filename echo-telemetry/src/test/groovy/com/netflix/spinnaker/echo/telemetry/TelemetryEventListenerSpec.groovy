package com.netflix.spinnaker.echo.telemetry

import com.google.protobuf.util.JsonFormat
import com.netflix.spinnaker.echo.config.TelemetryConfig
import com.netflix.spinnaker.echo.model.Event
import com.netflix.spinnaker.kork.proto.stats.*
import com.netflix.spinnaker.kork.proto.stats.Event as EventProto
import retrofit.client.Response
import spock.lang.Specification
import spock.lang.Subject

class TelemetryEventListenerSpec extends Specification {

  def service = Mock(TelemetryService)

  def instanceId = "test-instance"
  def spinnakerVersion = "1.2.3"

  def applicationName = "someApp"
  def applicationHash = "e40464bf6d04933c6011c29974eb328777669813a76583e5d547941427df686f"

  def executionId = "execution_id"
  def executionHash = "6d6de5b8d67c11fff6d817ea3e1190bc63857de0329d253b21aef6e5c6bbebf9"

  def "test Event validation"() {
    given:
    def configProps = new TelemetryConfig.TelemetryConfigProps()

    @Subject
    def listener = new TelemetryEventListener(service, configProps)

    when: "null details"
    listener.processEvent(new Event())

    then:
    0 * service.log(_)

    when: "null content"
    listener.processEvent(new Event(
      details: [:]
    ))

    then:
    0 * service.log(_)

    when: "irrelevant details type are ignored"
    listener.processEvent(new Event(
      details: [
        type: "foobar1",
      ],
      content: [:]
    ))

    then:
    0 * service.log(_)

    when: "missing application ID"
    listener.processEvent(new Event(
      details: [
        type: "orca:orchestration:complete",
      ],
      content: [:]
    ))

    then:
    0 * service.log(_)

    when: "no execution in content"
    listener.processEvent(new Event(
      details: [
        type: "orca:orchestration:complete",
        application: "foobar",
      ],
      content: [
        execution: [:],
      ],
    ))

    then:
    0 * service.log(_)
  }

  def "send a telemetry event"() {
    given:
    def configProps = new TelemetryConfig.TelemetryConfigProps()
      .setInstanceId(instanceId)
      .setSpinnakerVersion(spinnakerVersion)

    @Subject
    def listener = new TelemetryEventListener(service, configProps)

    when:
    listener.processEvent(new Event(
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
              type  : "deploy",
              status: "SUCCEEDED",
              syntheticStageOwner: null,
              context: [
                "cloudProvider": "nine"
              ]
            ],
            [
              type: "removed",
              syntheticStageOwner: "somethingNonNull",
              status: "SUCCEEDED"
            ],
            [
              type  : "wait",
              status: "TERMINAL"
            ],
          ]
        ]
      ]
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
}
