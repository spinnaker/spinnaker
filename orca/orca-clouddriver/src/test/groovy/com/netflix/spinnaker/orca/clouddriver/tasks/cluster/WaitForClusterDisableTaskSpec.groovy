package com.netflix.spinnaker.orca.clouddriver.tasks.cluster

import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.ModelUtils
import com.netflix.spinnaker.orca.clouddriver.model.Cluster
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.WaitForRequiredInstancesDownTask
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import org.springframework.core.env.Environment
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.RUNNING
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.SUCCEEDED
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class WaitForClusterDisableTaskSpec extends Specification {
  CloudDriverService cloudDriverService = Mock()
  def environment = Mock(Environment) {
    getProperty(WaitForClusterDisableTask.TOGGLE, Boolean, false) >> true
  }

  @Shared
  def region = "region"
  @Shared
  def clusterName = "clusterName"

  @Shared
  ServerGroupCreator serverGroupCreator = Stub(ServerGroupCreator) {
    getCloudProvider() >> "cloudProvider"
    isKatoResultExpected() >> false
    getOperations(_) >> [["aOp": "foo"]]
  }

  @Subject
  WaitForClusterDisableTask task = new WaitForClusterDisableTask([serverGroupCreator]).with {
    environment = this.environment
    return it
  }

  @Unroll
  def "status=#status when desiredPercentage=#desiredPct, interestingHealthProviderNames=#interestingHealthProviderNames"() {
    given:
    def stage = stage {
      context = [
          cluster: clusterName,
          credentials: "test",
          "deploy.server.groups": [
              (dsgregion): ["$clusterName-$oldServerGroup".toString()]
          ],
          (desiredPct ? "desiredPercentage" : "blerp"): desiredPct,
          interestingHealthProviderNames: interestingHealthProviderNames
      ]
    }
    stage.setStartTime(System.currentTimeMillis())

    cloudDriverService.maybeCluster(*_) >> Optional.of(ModelUtils.cluster([
        name: clusterName,
        serverGroups: [
            serverGroup("$clusterName-v050".toString(), "us-west-1", [:]),
            serverGroup("$clusterName-v051".toString(), "us-west-1", [:]),
            serverGroup("$clusterName-$newServerGroup".toString(), region, [:]),
            serverGroup("$clusterName-$oldServerGroup".toString(), region, [
                disabled: disabled,
                capacity: [desired: desired],
                instances: [
                    instance('i-1', platformHealthState, extraHealths),
                    instance('i-2', platformHealthState, extraHealths),
                    instance('i-3', platformHealthState, extraHealths),
                ]
            ])
        ]
    ]))

    task.cloudDriverService = cloudDriverService
    task.waitForRequiredInstancesDownTask = new WaitForRequiredInstancesDownTask()
    task.MINIMUM_WAIT_TIME_MS = minWaitTime

    when:
    TaskResult result = task.execute(stage)

    then:
    result.getStatus() == status

    where:
    dsgregion | minWaitTime | disabled | desired | desiredPct | interestingHealthProviderNames | extraHealths                  | platformHealthState || status
    "other"   | 0           | false    | 3       | null       | ['platformHealthType']         | []                            | 'Unknown'           || SUCCEEDED  // exercises if (!remainingDeployServerGroups)...
    "other"   | 90          | false    | 3       | null       | ['platformHealthType']         | []                            | 'Unknown'           || RUNNING    // keeps running if duration < minWaitTime
    region    | 0           | false    | 3       | null       | null                           | []                            | 'Unknown'           || RUNNING    // keeps running if disabled is false

    // tests for isDisabled==true
    region    | 0           | true     | 3       | null       | ['platformHealthType']         | []                            | 'Unknown'           || SUCCEEDED
    region    | 0           | true     | 3       | null       | ['platformHealthType']         | []                            | 'NotUnknown'        || RUNNING    // wait for instances down even if cluster is disabled
    region    | 0           | true     | 3       | 100        | ['platformHealthType']         | []                            | 'NotUnknown'        || RUNNING    // also wait for instances down with a desiredPct
    region    | 0           | true     | 4       | 50         | ['platformHealthType']         | []                            | 'Unknown'           || SUCCEEDED
    region    | 0           | true     | 3       | null       | ['strangeType']                | []                            | 'Unknown'           || SUCCEEDED  // intersection of interesting and provided healths is empty, so we're done
    region    | 0           | true     | 3       | null       | ['strangeType']                | health('strange', 'Down')     | 'Unknown'           || SUCCEEDED  // also done if we provide it and are down...
    region    | 0           | true     | 3       | null       | ['strangeType']                | health('strange', 'Up')       | 'Unknown'           || RUNNING    // ...but not if that extra health is up

    // tests for no desiredPct
    region    | 0           | true     | 3       | null       | []                             | []                            | 'Unknown'           || SUCCEEDED  // no health providers to check so short-circuits early
    region    | 0           | true     | 3       | null       | null                           | []                            | 'Unknown'           || SUCCEEDED  // exercises null vs empty behavior of interestingHealthProviderNames
    region    | 0           | true     | 3       | null       | ['platformHealthType']         | []                            | 'Unknown'           || SUCCEEDED  // considered complete because only considers the platform health
    region    | 0           | true     | 3       | null       | ['platformHealthType']         | health('eureka', 'Up')        | 'Unknown'           || SUCCEEDED  // up health is filtered out so we're done
    region    | 0           | true     | 3       | null       | ['strangeType']                | []                            | 'Unknown'           || SUCCEEDED  // missing health is filtered out so we're done
    region    | 0           | true     | 3       | null       | ['strangeType']                | health('strange', 'Down')     | 'Unknown'           || SUCCEEDED  // extra health is down so we're done

    // tests with desiredPct
    region    | 0           | true     | 3       | 100        | null                           | []                            | 'Unknown'           || SUCCEEDED  // no other health providers than platform
    region    | 0           | true     | 3       | 100        | null                           | []                            | 'NotUnknown'        || RUNNING    // no other health providers than platform
    region    | 0           | true     | 4       | 100        | ['platformHealthType']         | []                            | 'Unknown'           || RUNNING    // can't reach count(someAreDownAndNoneAreUp) >= targetDesiredSize
    region    | 0           | true     | 4       | 50         | ['platformHealthType']         | []                            | 'Unknown'           || SUCCEEDED  // all look down, and we want at least 2 down so we're done
    region    | 0           | true     | 3       | 100        | ['strangeType']                | []                            | 'Unknown'           || SUCCEEDED  // intersection of interesting and provided healths is empty, so we're done
    region    | 0           | true     | 3       | 100        | ['strangeType']                | health('strange', 'Down')     | 'Unknown'           || SUCCEEDED  // extra health is down so we're done
    region    | 0           | true     | 3       | 100        | ['strangeType']                | health('strange', 'Up')       | 'Unknown'           || RUNNING    // extra health is up so we're not done
    region    | 0           | true     | 3       | 100        | ['strangeType']                | health('strange', 'Starting') | 'Unknown'           || SUCCEEDED  // Starting is considered down

    oldServerGroup = "v167"
    newServerGroup = "v168"
  }

  @Unroll
  def "fails with '#expectedMessage' when clusterData=#clusterData"() {
    given:
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "test", [
        cluster: clusterName,
        credentials: 'test',
        "deploy.server.groups": [
            (region): ["$clusterName-v42".toString()]
        ]
    ])

    cloudDriverService.maybeCluster(*_) >> clusterData
    task.cloudDriverService = cloudDriverService

    when:
    task.execute(stage)

    then:
    IllegalStateException e = thrown()
    e.message.startsWith(expectedMessage)

    where:
    clusterData                                                   || expectedMessage
    Optional.empty()                                              || 'no cluster details found'
    Optional.of(new Cluster(name: clusterName, serverGroups: [])) || 'no server groups found'
  }

  private static Map instance(name, platformHealthState = 'Unknown', extraHealths = []) {
    return [
        name: name,
        launchTime: null,
        health: [[healthClass: 'platform', type: 'platformHealthType', state: platformHealthState]] + extraHealths,
        healthState: null,
        zone: 'thezone'
    ]
  }

  private static Map serverGroup(name, region, Map other) {
    return [
        name: name,
        region: region,
    ] + other
  }

  private static Map health(String name, String state) {
    return [
        healthClass: name + 'Class',
        type: name + 'Type',
        state: state
    ]
  }
}

