package com.netflix.spinnaker.orca.clouddriver.tasks.cluster

import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.WaitForRequiredInstancesDownTask
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class WaitForClusterDisableTaskSpec extends Specification {
  def oortHelper = Mock(OortHelper)

  @Shared def region = "region"
  @Shared def clusterName = "clusterName"

  @Shared
  ServerGroupCreator serverGroupCreator = Stub(ServerGroupCreator) {
    getCloudProvider() >> "cloudProvider"
    isKatoResultExpected() >> false
    getOperations(_) >> [["aOp": "foo"]]
  }

  @Subject def task = new WaitForClusterDisableTask([serverGroupCreator])

  @Unroll
  def "status=#status when oldSGDisabled=#oldSGDisabled, desiredPercentage=#desiredPct, interestingHealthProviderNames=#interestingHealthProviderNames"() {
    given:
    def stage = stage {
      context = [
        cluster                                     : clusterName,
        credentials                                 : "test",
        "deploy.server.groups"                      : [
          (dsgregion): ["$clusterName-$oldServerGroup".toString()]
        ],
        (desiredPct ? "desiredPercentage" : "blerp"): desiredPct,
        interestingHealthProviderNames              : interestingHealthProviderNames
      ]
    }
    stage.setStartTime(System.currentTimeMillis())

    oortHelper.getCluster(*_) >> [
      name: clusterName,
      serverGroups: [
        serverGroup("$clusterName-v050".toString(), "us-west-1", [:]),
        serverGroup("$clusterName-v051".toString(), "us-west-1", [:]),
        serverGroup("$clusterName-$newServerGroup".toString(), region, [:]),
        serverGroup("$clusterName-$oldServerGroup".toString(), region, [
          disabled: oldSGDisabled,
          capacity: [desired: desired],
          instances: [
            instance('i-1', platformHealthState, extraHealths),
            instance('i-2', platformHealthState, extraHealths),
            instance('i-3', platformHealthState, extraHealths),
          ]
        ])
      ]
    ]

    task.oortHelper = oortHelper
    task.waitForRequiredInstancesDownTask = new WaitForRequiredInstancesDownTask()
    task.MINIMUM_WAIT_TIME_MS = minWaitTime

    when:
    TaskResult result = task.execute(stage)

    then:
    result.getStatus() == status

    where:
    dsgregion | minWaitTime | oldSGDisabled | desired | desiredPct | interestingHealthProviderNames | extraHealths              | platformHealthState || status
    "other"   | 0           | false         | 3       | null       | ['platformHealthType']         | []                        | 'Unknown'           || SUCCEEDED  // exercises if (!remainingDeployServerGroups)...
    "other"   | 90          | false         | 3       | null       | ['platformHealthType']         | []                        | 'Unknown'           || RUNNING    // keeps running if duration < minWaitTime

    // tests for isDisabled==true
    region    | 0           | true          | 3       | null       | ['platformHealthType']         | []                        | 'Unknown'           || SUCCEEDED
    region    | 0           | true          | 3       | null       | ['platformHealthType']         | []                        | 'NotUnknown'        || RUNNING    // wait for instances down even if cluster is disabled
    region    | 0           | true          | 3       | 100        | ['platformHealthType']         | []                        | 'NotUnknown'        || RUNNING    // also wait for instances down with a desiredPct
    region    | 0           | true          | 4       | 50         | ['platformHealthType']         | []                        | 'Unknown'           || SUCCEEDED
    region    | 0           | true          | 3       | null       | ['strangeType']                | []                        | 'Unknown'           || SUCCEEDED  // intersection of interesting and provided healths is empty, so we're done
    region    | 0           | true          | 3       | null       | ['strangeType']                | health('strange', 'Down') | 'Unknown'           || SUCCEEDED  // also done if we provide it and are down...
    region    | 0           | true          | 3       | null       | ['strangeType']                | health('strange', 'Up')   | 'Unknown'           || RUNNING    // ...but not if that extra health is up

    // tests for isDisabled==false, no desiredPct
    region    | 0           | false         | 3       | null       | []                             | []                        | 'Unknown'           || SUCCEEDED  // no health providers to check so short-circuits early
    region    | 0           | false         | 3       | null       | null                           | []                        | 'Unknown'           || RUNNING    // exercises null vs empty behavior of interestingHealthProviderNames
    region    | 0           | false         | 3       | null       | ['platformHealthType']         | []                        | 'Unknown'           || SUCCEEDED  // considered complete because only considers the platform health
    region    | 0           | false         | 3       | null       | ['platformHealthType']         | []                        | 'Up'                || SUCCEEDED  // considered complete because only considers the platform health, despite platform health being Up
    region    | 0           | false         | 3       | null       | ['strangeType']                | []                        | 'Unknown'           || RUNNING    // can't complete if we need to monitor an unknown health provider...
    region    | 0           | false         | 3       | null       | ['strangeType']                | health('strange', 'Down') | 'Unknown'           || RUNNING    // ...regardless of down status

    // tests for waitForRequiredInstancesDownTask.hasSucceeded
    region    | 0           | false         | 3       | 100        | null                           | []                        | 'Unknown'           || SUCCEEDED  // no other health providers than platform, and it looks down
    region    | 0           | false         | 3       | 100        | null                           | []                        | 'NotUnknown'        || RUNNING    // no other health providers than platform, and it looks NOT down
    region    | 0           | false         | 4       | 100        | ['platformHealthType']         | []                        | 'Unknown'           || RUNNING    // can't reach count(someAreDownAndNoneAreUp) >= targetDesiredSize
    region    | 0           | false         | 4       | 50         | ['platformHealthType']         | []                        | 'Unknown'           || SUCCEEDED  // all look down, and we want at least 2 down so we're done
    region    | 0           | false         | 3       | 100        | ['strangeType']                | []                        | 'Unknown'           || SUCCEEDED  // intersection of interesting and provided healths is empty, so we're done
    region    | 0           | false         | 3       | 100        | ['strangeType']                | health('strange', 'Down') | 'Unknown'           || SUCCEEDED  // ...unless we have data for that health provider
    region    | 0           | false         | 3       | 100        | ['strangeType']                | health('strange', 'Up')   | 'Unknown'           || RUNNING    // ...unless we have data for that health provider

    oldServerGroup = "v167"
    newServerGroup = "v168"
  }

  @Unroll
  def "fails with '#message' when clusterData=#clusterData"() {
    given:
    def stage = new Stage(Execution.newPipeline("orca"), "test", [
      cluster: clusterName,
      credentials: 'test',
      "deploy.server.groups": [
        (region): ["$clusterName-v42".toString()]
      ]
    ])

    oortHelper.getCluster(*_) >> clusterData
    task.oortHelper = oortHelper

    when:
    task.execute(stage)

    then:
    IllegalStateException e = thrown()
    e.message.startsWith(expectedMessage)

    where:
    clusterData                            || expectedMessage
    Optional.empty()                       || 'no cluster details found'
    [name: clusterName, serverGroups: []]  || 'no server groups found'
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
      name  : name,
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

