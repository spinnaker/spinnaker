/*
 * Copyright 2026 McIntosh.farm
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

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification
import spock.lang.Unroll

class ModifyWarmPoolStageSpec extends Specification {
  CloudDriverService cloudDriverService = Mock()

  @Unroll
  def "should only succeed when warm pool presence reflects desired state"() {
    given:
    def task = new ModifyWarmPoolStage.WaitForWarmPool(cloudDriverService: cloudDriverService)

    when:
    def taskResult = task.execute(new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", "", [
      credentials: "test",
      region     : "us-east-1",
      asgName    : "test-asg",
      action     : action
    ]))

    then:
    taskResult.status == expectedTaskResultStatus

    1 * cloudDriverService.getTargetServerGroupAsMap("test", "test-asg", "us-east-1") >> {
      Optional.of([
        asg: [
          warmPoolConfiguration: warmPoolConfiguration
        ]
      ])
    }

    where:
    action    | warmPoolConfiguration      || expectedTaskResultStatus
    "upsert"  | [minSize: 2]               || ExecutionStatus.SUCCEEDED
    "upsert"  | null                       || ExecutionStatus.RUNNING
    "delete"  | null                       || ExecutionStatus.SUCCEEDED
    "delete"  | [minSize: 2]               || ExecutionStatus.RUNNING
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
    def stageData = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", "", context).mapTo(
      ModifyWarmPoolStage.WaitForWarmPool.StageData
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
