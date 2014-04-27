package com.netflix.kato.data.task

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Shared
import spock.lang.Specification

class DefaultTaskSpec extends Specification {

  @Shared
  DefaultTask task

  def setupSpec() {
    resetTask()
  }

  void cleanup() {
    resetTask()
  }

  void resetTask() {
    this.task = new DefaultTask("1", "INIT", "Starting")
  }

  void "status updates and appends to history"() {
    given:
      task.updateStatus "TEST", "Status Update"

    expect:
      task.status.phase == "TEST"
      task.status.status == "Status Update"
      task.history*.status.contains("Status Update")
  }

  void "task state is checked on updates"() {
    setup:
      task.complete()

    when:
      task.updateStatus "TEST", "Another Status Update"

    then:
      thrown IllegalStateException
  }

  void "task state is checked on complete/fail"() {
    setup:
      task.complete()

    when:
      task.complete()

    then:
      thrown IllegalStateException

    when:
      task.fail()

    then:
      thrown IllegalStateException
  }

  void "failing a task completes it too"() {
    given:
      task.fail()

    expect:
      task.status.isCompleted()
  }

  void "history status object doesnt serialize complete and fail"() {
    setup:
      def om = new ObjectMapper()
      task.updateStatus "TEST", "Testing Serialization"

    when:
      def json = om.writeValueAsString(task.history)

    then:
      !json.contains("complete")
      !json.contains("failed")

  }
}
