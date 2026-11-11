package com.netflix.spinnaker.echo.cdevents

import com.netflix.spinnaker.echo.api.events.Event
import spock.lang.Specification
import spock.lang.Unroll

class CDEventsBuilderServiceSpec extends Specification {

  def builder = new CDEventsBuilderService()

  @Unroll
  def "execution url uses #expectedPath when the notification type is #type"() {
    given:
    def event = new Event(content: [execution: [id: "1", name: "foo-pipeline"]])
    // new String(..) yields a value that is not interned, like one deserialized from JSON
    def config = [type: new String(type), link: "link"]

    when:
    def cloudEvent = builder.createCDEvent(
      [cdEventsType: "dev.cdevents.pipelinerun.queued"],
      "whatever",
      event,
      config,
      "starting",
      "http://spinnaker")

    then:
    new String(cloudEvent.getData().toBytes()).contains("http://spinnaker/#/applications/whatever/${expectedPath}/1")

    where:
    type       || expectedPath
    "stage"    || "executions/details"
    "pipeline" || "link"
    "task"     || "link"
  }
}
