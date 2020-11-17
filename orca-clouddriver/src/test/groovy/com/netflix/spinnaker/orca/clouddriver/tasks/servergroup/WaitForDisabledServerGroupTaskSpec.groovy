package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.pipeline.StageExecutionFactory
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.*

class WaitForDisabledServerGroupTaskSpec extends Specification {
  @Subject
  WaitForDisabledServerGroupTask task = new WaitForDisabledServerGroupTask(null, null)

  @Unroll
  def "handles wonky desiredPercentage=#desiredPercentage gracefully"() {
    given:
    StageExecution stage = StageExecutionFactory.newStage(
        PipelineExecutionImpl.newOrchestration("orca"),
        "stageType", "stageName",
        [desiredPercentage: desiredPercentage],
    null, null)

    when:
    def taskInput = stage.mapTo(WaitForDisabledServerGroupTask.TaskInput)

    then:
    taskInput.desiredPercentage == mapsTo

    when:
    def taskResult = task.execute(stage)

    then:
    stage.context.desiredPercentage == desiredPercentage
    taskResult.status == expected

    where:
    desiredPercentage | mapsTo || expected
    null              | null   || SKIPPED
    -1                | -1     || SKIPPED
    ""                | null   || SKIPPED
    new Integer(100)  | 100    || SKIPPED
    "200"             | 200    || SKIPPED
    102.5             | 102    || SKIPPED
    // 1L << 33, Jackson throws "Numeric value (8589934592) out of range of int"
  }
}
