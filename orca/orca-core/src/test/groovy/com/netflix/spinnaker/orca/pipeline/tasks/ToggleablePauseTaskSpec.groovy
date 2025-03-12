package com.netflix.spinnaker.orca.pipeline.tasks

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class ToggleablePauseTaskSpec extends Specification {
  static final String TOGGLE_NAME = "my.test.toggle"

  def dynamicConfigService = Mock(DynamicConfigService)

  def stage = stage {
    context = [
        pauseToggleKey: 'my.test.toggle'
    ]
  }

  @Subject
  def task = new ToggleablePauseTask(dynamicConfigService)

  def "should return SUCCEEDED when pause toggle is missing"() {
    given:
    stage.context.remove("pauseToggleKey")

    when:
    def result = task.execute(stage)

    then:
    0 * dynamicConfigService.isEnabled(TOGGLE_NAME, false)

    result.status == ExecutionStatus.SUCCEEDED
  }

  def "should return SUCCEEDED when pause toggle is false"() {
    when:
    def result = task.execute(stage)

    then:
    1 * dynamicConfigService.isEnabled(TOGGLE_NAME, false) >> { return false }

    result.status == ExecutionStatus.SUCCEEDED
  }

  def "should return RUNNING when pause toggle is true"() {
    when:
    def result = task.execute(stage)

    then:
    1 * dynamicConfigService.isEnabled(TOGGLE_NAME, false) >> { return true }

    result.status == ExecutionStatus.RUNNING
  }
}
