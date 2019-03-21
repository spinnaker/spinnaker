package com.netflix.spinnaker.orca.kato.pipeline.support

import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.test.model.ExecutionBuilder
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ResizeStrategySupportSpec extends Specification {
  @Subject
  ResizeStrategySupport resizeStrategySupport

  @Unroll
  def "test min logic in performScalingAndPinning() with unpinMin=#unpinMin originalMin=#originalMin savedMin=#savedMin"() {
    given:
    resizeStrategySupport = new ResizeStrategySupport()
    Stage stage = ExecutionBuilder.stage {}
    stage.context = [
      unpinMinimumCapacity: unpinMin,
      source: [
          serverGroupName: "app-v000"
      ],
      "originalCapacity.app-v000": [
          min: originalMin
      ],
      savedCapacity: [
          min: savedMin
      ]
    ]
    ResizeStrategy.OptionalConfiguration config = Mock(ResizeStrategy.OptionalConfiguration)

    when:
    def outputCapacity = resizeStrategySupport.performScalingAndPinning(sourceCapacity as ResizeStrategy.Capacity, stage, config)

    then:
    outputCapacity == expectedCapacity as ResizeStrategy.Capacity

    where:
    sourceCapacity               | unpinMin | originalMin | savedMin || expectedCapacity
    [min: 1, max: 3, desired: 2] | null     | 1           | null     || [min: 1, max: 3, desired: 2]
    [min: 1, max: 3, desired: 2] | false    | 1           | null     || [min: 1, max: 3, desired: 2]
    [min: 1, max: 3, desired: 2] | true     | 1           | null     || [min: 1, max: 3, desired: 2]
    [min: 1, max: 3, desired: 2] | true     | 2           | null     || [min: 1, max: 3, desired: 2] // won't unpin to a higher min 2
    [min: 1, max: 3, desired: 2] | true     | 0           | null     || [min: 0, max: 3, desired: 2]
    [min: 1, max: 3, desired: 2] | true     | null        | 2        || [min: 1, max: 3, desired: 2]
    [min: 1, max: 3, desired: 2] | true     | 0           | 2        || [min: 0, max: 3, desired: 2] // verify that 0 is a valid originalMin
    [min: 1, max: 3, desired: 2] | true     | null        | 0        || [min: 0, max: 3, desired: 2] // picks the savedMin value
  }
}
