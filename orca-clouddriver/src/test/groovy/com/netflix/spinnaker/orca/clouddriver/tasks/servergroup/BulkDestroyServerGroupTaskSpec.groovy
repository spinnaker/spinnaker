package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.orca.api.operations.OperationsInput
import com.netflix.spinnaker.orca.api.operations.OperationsRunner
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.ModelUtils
import com.netflix.spinnaker.orca.clouddriver.model.Cluster
import com.netflix.spinnaker.orca.clouddriver.model.KatoOperationsContext
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.utils.MonikerHelper
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.clouddriver.utils.TrafficGuard
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification

class BulkDestroyServerGroupTaskSpec extends Specification {

  TrafficGuard trafficGuard = Mock()
  CloudDriverService cloudDriverService = Mock()

  def "should create multiple destroy operations on bulk destroy server group task"() {
    given:
    def task = new BulkDestroyServerGroupTask(trafficGuard: trafficGuard, monikerHelper: new MonikerHelper(), cloudDriverService: cloudDriverService)
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "")
    stage.context = [
      cloudProvider:  "titus",
      credentials:  "test",
      region: "us-west-1",
      serverGroupNames: [
        "app-v00",
        "app-v01"
      ]
    ]

    and:
    Cluster cluster = ModelUtils.cluster([
      name: "cluster",
      type: "titus",
      accountName: "titustestvpc",
      serverGroups: [
        [
          name: "app-v00",
          cloudprovider: "titus",
          region: "us-west-1"
        ],
        [
          name: "app-v01",
          cloudprovider: "titus",
          region: "us-west-1"
        ]
      ]
    ])

    List<Map> operations = []
    task.operationsRunner = Mock(OperationsRunner) {
      1 * run(_) >> {
        OperationsInput operationsInput = it[0]
        operations += operationsInput.getOperations()
        new KatoOperationsContext(new TaskId(UUID.randomUUID().toString()), null)
      }
    }

    when:
    task.execute(stage)

    then:
    1 * cloudDriverService.maybeCluster(_, _, _, _) >> Optional.of(cluster)
    operations.size() == 2
    operations[0].destroyServerGroup.serverGroupName == stage.context.serverGroupNames[0]
    operations[1].destroyServerGroup.serverGroupName == stage.context.serverGroupNames[1]

    when: 'a cluster is not found'
    task.execute(stage)

    then:
    1 * cloudDriverService.maybeCluster(_, _, _, _) >> Optional.empty()
    thrown(IllegalArgumentException)

    when: 'no server group match found in the cluster'
    cluster.serverGroups[0].name = 'unknown'
    cluster.serverGroups[1].name = 'unknown 2'

    task.execute(stage)

    then:
    1 * cloudDriverService.maybeCluster(_, _, _, _) >> Optional.of(cluster)
    thrown(TargetServerGroup.NotFoundException)
  }
}
