/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Unroll

class ModifyAwsScalingProcessStageSpec extends Specification {
  def oortHelper = Mock(OortHelper)

  @Unroll
  def "should only succeed when suspendedProcesses reflect desired state"() {
    given:
    def task = new ModifyAwsScalingProcessStage.WaitForScalingProcess(oortHelper: oortHelper)

    when:
    def taskResult = task.execute(new Stage<>(new Pipeline("orca"), "", "", [
      credentials: "test",
      region     : "us-east-1",
      asgName    : "test-asg",
      action     : action,
      processes  : processes
    ]))

    then:
    taskResult.status == expectedTaskResultStatus

    1 * oortHelper.getTargetServerGroup("test", "test-asg", "us-east-1", "aws") >> {
      new TargetServerGroup(
        asg: [
          suspendedProcesses: suspendedProcesses?.collect { [processName: it] }
        ]
      )
    }

    where:
    action    | processes               | suspendedProcesses                           || expectedTaskResultStatus
    "resume"  | ["Launch"]              | ["Launch"]                                   || ExecutionStatus.RUNNING
    "resume"  | ["Launch"]              | []                                           || ExecutionStatus.SUCCEEDED
    "resume"  | ["Launch", "Terminate"] | ["Terminate"]                                || ExecutionStatus.RUNNING
    "suspend" | ["Launch", "Terminate"] | ["Terminate"]                                || ExecutionStatus.RUNNING
    "suspend" | ["Launch", "Terminate"] | ["Launch", "Terminate", "AddToLoadBalancer"] || ExecutionStatus.SUCCEEDED

  }

  @Unroll
  def "should return suspendedProcesses from the asg details"() {
    given:
    def targetServerGroup = new TargetServerGroup(
      asg: [
        suspendedProcesses: suspendedProcesses?.collect { [processName: it] }
      ]
    )

    expect:
    ModifyAwsScalingProcessStage.WaitForScalingProcess.getSuspendedProcesses(targetServerGroup) == suspendedProcesses

    where:
    suspendedProcesses      || _
    []                      || _
    null                    || _
    ["Launch"]              || _
    ["Launch", "Terminate"] || _
  }

  @Unroll
  def "should support region/regions/asgName/serverGroupName"() {
    given:
    def context = [
      region         : region,
      regions        : regions,
      asgName        : asgName,
      serverGroupName: serverGroupName
    ]

    when:
    def stageData = new Stage<>(new Pipeline("orca"), "", "", context).mapTo(
      ModifyAwsScalingProcessStage.WaitForScalingProcess.StageData
    )

    then:
    stageData.region == expectedRegion
    stageData.serverGroupName == expectedServerGroupName

    where:
    region      | regions       | asgName    | serverGroupName || expectedRegion || expectedServerGroupName
    "us-east-1" | null          | "test-asg" | null            || "us-east-1"    || "test-asg"
    null        | ["us-east-1"] | "test-asg" | null            || "us-east-1"    || "test-asg"
    null        | ["us-east-1"] | null       | "test-asg"      || "us-east-1"    || "test-asg"
  }
}
