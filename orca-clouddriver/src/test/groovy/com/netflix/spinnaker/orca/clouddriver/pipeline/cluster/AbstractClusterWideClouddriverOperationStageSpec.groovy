package com.netflix.spinnaker.orca.clouddriver.pipeline.cluster

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.dynamicconfig.SpringDynamicConfigService
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractClusterWideClouddriverTask
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractWaitForClusterWideClouddriverTask
import com.netflix.spinnaker.orca.clouddriver.utils.ClusterLockHelper
import com.netflix.spinnaker.orca.clouddriver.utils.MonikerHelper
import com.netflix.spinnaker.orca.clouddriver.utils.TrafficGuard
import com.netflix.spinnaker.orca.locks.LockingConfigurationProperties
import com.netflix.spinnaker.orca.pipeline.AcquireLockStage
import com.netflix.spinnaker.orca.pipeline.ReleaseLockStage
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.test.model.ExecutionBuilder
import org.springframework.mock.env.MockEnvironment
import spock.lang.Specification
import spock.lang.Unroll

class AbstractClusterWideClouddriverOperationStageSpec extends Specification {

  def guard = Mock(TrafficGuard)
  def env = new MockEnvironment()
  def config = new LockingConfigurationProperties(new SpringDynamicConfigService(environment: env))
  def dynamicConfigService = Mock(DynamicConfigService)

  def stageBuilder = new TestStage(guard, config, dynamicConfigService)

  @Unroll
  def "should #desc1 inject #expectedType for #desc2 traffic guard protected cluster"() {
    given:
    env.setProperty('locking.enabled', 'true')
    Stage testStage = ExecutionBuilder.stage {
      context = stageContext
    }
    StageGraphBuilder graph = beforeStages ? StageGraphBuilder.beforeStages(testStage) : StageGraphBuilder.afterStages(testStage)

    when:
    beforeStages ? stageBuilder.beforeStages(testStage, graph) : stageBuilder.afterStages(testStage, graph)
    Iterator<Stage> stages = graph.build().iterator()

    then:
    1 * guard.hasDisableLock(moniker, account, location) >> shouldLock
    stages.hasNext() == shouldLock
    if (shouldLock) {
      def lockStage = stages.next()
      lockStage.type == expectedType
      lockStage.context.lock.lockName == lockName
    }

    where:
    cluster = 'foo'
    region = 'bar'
    location = new Location(Location.Type.REGION, region)
    account = 'baz'
    moniker = MonikerHelper.friggaToMoniker(cluster)
    stageContext = [
      cluster: cluster,
      region: region,
      credentials: account
    ]
    lockName = ClusterLockHelper.clusterLockName(moniker, account, location)
    beforeStages << [false, false, true, true]
    shouldLock << [false, true, false, true]
    expectedType = beforeStages ? AcquireLockStage.PIPELINE_TYPE : ReleaseLockStage.PIPELINE_TYPE
    desc1 = shouldLock ? "" : "not"
    desc2 = shouldLock ? "" : "non"
  }


  static class TestStage extends AbstractClusterWideClouddriverOperationStage {
    TestStage(TrafficGuard trafficGuard,
              LockingConfigurationProperties config,
              DynamicConfigService dynamicConfigService) {
      super(trafficGuard, config, dynamicConfigService)
    }

    @Override
    protected Class<? extends AbstractClusterWideClouddriverTask> getClusterOperationTask() {
      return TestTask
    }

    @Override
    protected Class<? extends AbstractWaitForClusterWideClouddriverTask> getWaitForTask() {
      return WaitTask
    }
  }

  static class TestTask extends AbstractClusterWideClouddriverTask {
    @Override
    String getClouddriverOperation() {
      return null
    }
  }

  static class WaitTask extends AbstractWaitForClusterWideClouddriverTask {
    @Override
    boolean isServerGroupOperationInProgress(Stage stage, List<Map> interestingHealthProviderNames, Optional<TargetServerGroup> serverGroup) {
      return false
    }
  }
}
