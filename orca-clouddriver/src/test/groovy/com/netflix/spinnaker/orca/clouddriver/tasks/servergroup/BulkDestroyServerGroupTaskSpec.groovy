package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.utils.MonikerHelper
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.clouddriver.utils.TrafficGuard
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification

class BulkDestroyServerGroupTaskSpec extends Specification {

  def "should create multiple destroy operations on bulk destroy server group task"() {
    given:
    def task = new BulkDestroyServerGroupTask(trafficGuard: Mock(TrafficGuard), monikerHelper: new MonikerHelper())
    def stage = new Stage<>(new Pipeline("orca"), "")
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
    def cluster = [
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
    ]

    List<Map> operations = []
    task.katoService = Mock(KatoService) {
      1 * requestOperations('titus', _) >> {
        operations += it[1]
        rx.Observable.from(new TaskId(UUID.randomUUID().toString()))
      }
    }

    task.oortHelper = Mock(OortHelper) {
      1 * getCluster(_, _, _, _) >> Optional.of(cluster)
    }

    when:
    task.execute(stage)

    then:
    operations.size() == 2
    operations[0].destroyServerGroup.serverGroupName == stage.context.serverGroupNames[0]
    operations[1].destroyServerGroup.serverGroupName == stage.context.serverGroupNames[1]


    when: 'a cluster is not found'
    task.oortHelper = Mock(OortHelper) {
      1 * getCluster(_, _, _, _) >> Optional.empty()
    }

    task.execute(stage)

    then:
    thrown(IllegalArgumentException)

    when: 'no server group match found in the cluster'
    cluster.serverGroups[0].name = 'unknown'
    cluster.serverGroups[1].name = 'unknown 2'
    task.oortHelper = Mock(OortHelper) {
      1 * getCluster(_, _, _, _) >> Optional.of(cluster)
    }

    task.execute(stage)

    then:
    thrown(TargetServerGroup.NotFoundException)
  }

}
