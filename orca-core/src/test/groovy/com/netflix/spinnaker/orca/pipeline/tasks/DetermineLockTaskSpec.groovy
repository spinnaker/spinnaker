package com.netflix.spinnaker.orca.pipeline.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.AcquireLockStage
import com.netflix.spinnaker.orca.pipeline.ReleaseLockStage
import com.netflix.spinnaker.orca.pipeline.WaitStage
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import spock.lang.Specification
import spock.lang.Subject

class DetermineLockTaskSpec extends Specification {

  @Subject DetermineLockTask task = new DetermineLockTask(
    new StageNavigator(
      Arrays.asList(
        new WaitStage(),
        new AcquireLockStage(),
        new ReleaseLockStage())))

  def "should determine the lock when lock values explicitly provided"() {
    given:
    def exec = new Execution(Execution.ExecutionType.PIPELINE, app)
    def stage = new Stage(exec, ReleaseLockStage.PIPELINE_TYPE, [
      lock: [
        lockName: lockName,
        lockHolder: lockHolder,
        lockValue: [
          type: 'pipeline',
          application: app,
          id: lockId]]])

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.context.lock.lockName == lockName

    where:
    app = 'fooapp'
    lockName = 'lock'
    lockId = 'fooLock'
    lockHolder = 'holder'
  }

  def "should determine the lock from a previous stage"() {
    given:
    def exec = new Execution(Execution.ExecutionType.PIPELINE, app)
    def acquire = new Stage(exec, AcquireLockStage.PIPELINE_TYPE, [
      refId: 'acquireLock',
      lock: [
        lockName: lockName,
        lockHolder: lockHolder,
        lockValue: [
          type: 'pipeline',
          application: app,
          id: lockId]]])

    def wait = new Stage(exec, WaitStage.STAGE_TYPE, [
      refId: 'wait',
      requisiteStageRefIds: [acquire.refId]
    ])

    def release = new Stage(exec, ReleaseLockStage.PIPELINE_TYPE, [
      refId: 'releaseLock',
      requisiteStageRefIds: [wait.refId]
    ])
    exec.stages.addAll(Arrays.asList(acquire, wait, release))

    when:
    def result = task.execute(release)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.context.lock.lockName == lockName

    where:
    app = 'fooapp'
    lockName = 'lock'
    lockId = 'fooLock'
    lockHolder = 'holder'
  }
}
