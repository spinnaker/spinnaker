package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.pipeline.StageExecutionFactory
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.*

class WaitForDisabledServerGroupTaskSpec extends Specification {
  WaitForDisabledServerGroupTask.ServerGroupFetcher fetcher = Mock()

  @Subject
  WaitForDisabledServerGroupTask task = new WaitForDisabledServerGroupTask(null, fetcher)

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
    -1                | -1     || SKIPPED
    new Integer(42)   | 42     || SKIPPED
    "42"              | 42     || SKIPPED
    42.5              | 42     || SKIPPED
    200               | 200    || SKIPPED
    // 1L << 33, Jackson throws "Numeric value (8589934592) out of range of int"
  }

  @Unroll
  def "when doing a full disable (desiredPercentage=#desiredPercentage) and the server group has disabled=#serverGroupDisabled, the task should return #expectedStatus"() {
    StageExecution stage = StageExecutionFactory.newStage(
        PipelineExecutionImpl.newOrchestration("orca"),
        "stageType", "stageName",
        [
            desiredPercentage: desiredPercentage,
            credentials: "test",
            serverGroupName: "asg-v001",
            region: "numenor-1"
        ],
        null, null)

    when:
    fetcher.fetchServerGroup(_) >> new TargetServerGroup([disabled: serverGroupDisabled])
    def taskResult = task.execute(stage)

    then:
    taskResult.status == expectedStatus

    where:
    desiredPercentage | serverGroupDisabled || expectedStatus
    null              | false               || RUNNING
    ""                | false               || RUNNING
    100               | false               || RUNNING

    null              | true                || SUCCEEDED
    ""                | true                || SUCCEEDED
    100               | true                || SUCCEEDED
  }
}
