package com.netflix.spinnaker.orca.tasks.image

import com.netflix.spinnaker.orca.kato.tasks.rollingpush.CheckForRemainingTerminationsTask
import com.netflix.spinnaker.orca.time.MutableClock
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class RollingPushTaskSpec extends Specification {
  def clock = new MutableClock()
  @Subject
  terminateTask = new CheckForRemainingTerminationsTask()


  void "should wait correctly in rolling push"() {
    setup:
    def stage = stage {
      refId = "1"
      type = "wait"
      context["waitTime"] = 300
      context["startTime"] = clock.instant().toEpochMilli()
      context["terminationInstanceIds"] = ["1111", "2222"]
    }

    when:
    def result = terminateTask.execute(stage)

    then:
    result.context.startTime == Instant.EPOCH
  }
}
