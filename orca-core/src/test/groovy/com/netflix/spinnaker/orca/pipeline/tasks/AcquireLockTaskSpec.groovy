package com.netflix.spinnaker.orca.pipeline.tasks

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.locks.LockContext
import com.netflix.spinnaker.orca.locks.LockFailureException
import com.netflix.spinnaker.orca.locks.LockManager
import com.netflix.spinnaker.orca.locks.LockingConfigurationProperties
import com.netflix.spinnaker.orca.pipeline.AcquireLockStage
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class AcquireLockTaskSpec extends Specification {

  LockManager lockManager = Mock(LockManager)

  DynamicConfigService dynamicConfigService = Stub(DynamicConfigService) {
    getConfig(_ as Class, _ as String, _ as Object) >> { type, name, defaultValue -> return defaultValue }
    isEnabled(_ as String, _ as Boolean) >> { flag, defaultValue -> return defaultValue }
  }

  LockingConfigurationProperties props = new LockingConfigurationProperties(dynamicConfigService)

  @Subject
  AcquireLockTask task = new AcquireLockTask(lockManager, props)

  def "should build default lock from stage"() {
    given:
    def ex = new Execution(Execution.ExecutionType.PIPELINE, application)
    def stage = new Stage(ex, AcquireLockStage.PIPELINE_TYPE, [lock: [lockName: lockName]])
    def lc = new LockContext(lockName, lv(ex), stage.id)

    when:
    def result = task.execute(stage)

    then:
    1 * lockManager.acquireLock(lc.lockName, lc.lockValue, lc.lockHolder, props.ttlSeconds)
    result.status == ExecutionStatus.SUCCEEDED
    result.context.lock == lc

    where:
    application = 'fooapp'
    lockName = 'testlock'
  }

  def "a lock failure should STOP the stage and allow other branches to complete"() {
    given:
    def ex = new Execution(Execution.ExecutionType.PIPELINE, application)
    def stage = new Stage(ex, AcquireLockStage.PIPELINE_TYPE, [lock: [lockName: lockName]])
    def lc = new LockContext(lockName, lv(ex), stage.id)

    when:
    def result = task.execute(stage)

    then:
    1 * lockManager.acquireLock(lc.lockName, lc.lockValue, lc.lockHolder, props.ttlSeconds) >> {
      throw new LockFailureException(lockName, currentLockValue)
    }

    result.status == ExecutionStatus.STOPPED
    result.context.exception.details.lockName == lockName
    result.context.exception.details.currentLockValue == currentLockValue
    result.context.completeOtherBranchesThenFail == true

    where:
    currentApplication = 'barapp'
    currentLockValue = lv(new Execution(Execution.ExecutionType.PIPELINE, currentApplication))
    application = 'fooapp'
    lockName = 'testlock'

  }

  @Unroll
  def "should prefer explicit context values for lock context when #desc"() {
    given:
    def stage = new Stage(execution, AcquireLockStage.PIPELINE_TYPE, [lock: [lockName: lockName, lockValue: contextLockValue, lockHolder: lockHolder]])
    def lc = new LockContext(lockName, defaultLockValue, stage.id)

    when:
    def result = task.execute(stage)

    then:
    1 * lockManager.acquireLock(lc.lockName, expectedLockValue, lockHolder ? lockHolder : lc.lockHolder, props.ttlSeconds)
    result.status == ExecutionStatus.SUCCEEDED

    where:
    application = 'fooapp'
    lockName = 'testlock'

    lvApp    | lvId    | lockHolder   || desc
    null     | null    | null         || 'no explicit values provided'
    'barapp' | null    | null         || 'explicit lockValue application provided'
    null     | null    | 'someholder' || 'explicit lockHolder provided'
    'bazapp' | 'bazid' | 'someholder' || 'both lockValue and lockHolder provided'

    contextLockValue = ctxLv(lvApp, lvId)
    execution = new Execution(Execution.ExecutionType.PIPELINE, application)
    defaultLockValue = lv(execution)

    expectedLockValue = contextLockValue == null ? defaultLockValue :
      new LockManager.LockValue(
        contextLockValue.application ?: defaultLockValue.application,
        contextLockValue.type ?: defaultLockValue.type,
        contextLockValue.id ?: defaultLockValue.id)
  }

  private static Map<String, String> ctxLv(String application, String id) {
    if (!(application || id)) {
      return null
    }
    return [
      application: application,
      type       : 'pipeline',
      id         : id
    ]
  }

  private static LockManager.LockValue lv(Execution ex) {
    return new LockManager.LockValue(ex.application, ex.type.toString(), ex.id)
  }
}
