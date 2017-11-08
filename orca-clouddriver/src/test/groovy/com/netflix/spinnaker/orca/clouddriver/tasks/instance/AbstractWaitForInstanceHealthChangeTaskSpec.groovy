/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.clouddriver.tasks.instance

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class AbstractWaitForInstanceHealthChangeTaskSpec extends Specification {
  @Shared
  Execution pipeline = Execution.newPipeline("orca")

  @Unroll
  void 'should be #expectedResult for #instanceDetails with #interestingHealthProviderNames relevant health providers (WaitForDownInstances)'() {
    given:
    def localInstanceDetails = instanceDetails
    def task = new WaitForDownInstanceHealthTask() {
      @Override
      protected Map getInstance(String account, String region, String instanceId) {
        return localInstanceDetails.find { it.instanceId == instanceId }
      }
    }
    def stage = new Stage(pipeline, "waitForDownInstance", [
      instanceIds                   : localInstanceDetails*.instanceId,
      interestingHealthProviderNames: interestingHealthProviderNames
    ])

    expect:
    task.execute(stage).status == expectedResult

//TODO(duftler): Explain the new behavior here...
    where:
    instanceDetails                                                | interestingHealthProviderNames || expectedResult
    []                                                             | null                            | ExecutionStatus.TERMINAL
    []                                                             | []                              | ExecutionStatus.SUCCEEDED
    [[instanceId: "1", health: [h("LB", "Up"), h("D", "Up")]]]     | null                            | ExecutionStatus.RUNNING
    [[instanceId: "1", health: [h("LB", "Up"), h("D", "Up")]]]     | []                              | ExecutionStatus.SUCCEEDED
    [[instanceId: "1", health: [h("LB", "Up"), h("D", "Down")]]]   | null                            | ExecutionStatus.RUNNING
    [[instanceId: "1", health: [h("LB", "Up"), h("D", "Down")]]]   | []                              | ExecutionStatus.SUCCEEDED
    [
      [instanceId: "1", health: [h("LB", "Down"), h("D", "Down")]],
      [instanceId: "2", health: [h("LB", "Up"), h("D", "Up")]]
    ]                                                              | null                            | ExecutionStatus.RUNNING
    [
      [instanceId: "1", health: [h("LB", "Down"), h("D", "Down")]],
      [instanceId: "2", health: [h("LB", "Up"), h("D", "Up")]]
    ]                                                              | []                              | ExecutionStatus.SUCCEEDED
    [[instanceId: "1", health: [h("LB", "Down"), h("D", "Down")]]] | null                            | ExecutionStatus.SUCCEEDED
    [[instanceId: "1", health: [h("LB", "Down"), h("D", "Down")]]] | []                              | ExecutionStatus.SUCCEEDED
    [[instanceId: "1", health: [h("LB", "Up"), h("D", "Down")]]]   | ["D"]                           | ExecutionStatus.SUCCEEDED
    [[instanceId: "1", health: []]]                                | ["D"]                           | ExecutionStatus.SUCCEEDED
  }

  @Unroll
  void 'should be #expectedResult for #instanceDetails with #interestingHealthProviderNames relevant health providers (WaitForUpInstances)'() {
    given:
    def localInstanceDetails = instanceDetails
    def task = new WaitForUpInstanceHealthTask() {
      @Override
      protected Map getInstance(String account, String region, String instanceId) {
        return localInstanceDetails.find { it.instanceId == instanceId }
      }
    }
    def stage = new Stage(pipeline, "waitForDownInstance", [
      instanceIds                   : localInstanceDetails*.instanceId,
      interestingHealthProviderNames: interestingHealthProviderNames
    ])

    expect:
    task.execute(stage).status == expectedResult

//TODO(duftler): Explain the new behavior here...
    where:
    instanceDetails                                                | interestingHealthProviderNames || expectedResult
    []                                                             | null                            | ExecutionStatus.TERMINAL
    []                                                             | []                              | ExecutionStatus.SUCCEEDED
    [[instanceId: "1", health: [h("LB", "Up"), h("D", "Down")]]]   | null                            | ExecutionStatus.RUNNING
    [[instanceId: "1", health: [h("LB", "Up"), h("D", "Down")]]]   | []                              | ExecutionStatus.SUCCEEDED
    [
      [instanceId: "1", health: [h("LB", "Down"), h("D", "Down")]],
      [instanceId: "2", health: [h("LB", "Up"), h("D", "Up")]]
    ]                                                              | null                            | ExecutionStatus.RUNNING
    [
      [instanceId: "1", health: [h("LB", "Down"), h("D", "Down")]],
      [instanceId: "2", health: [h("LB", "Up"), h("D", "Up")]]
    ]                                                              | []                              | ExecutionStatus.SUCCEEDED
    [[instanceId: "1", health: [h("LB", "Down"), h("D", "Down")]]] | null                            | ExecutionStatus.RUNNING
    [[instanceId: "1", health: [h("LB", "Down"), h("D", "Down")]]] | []                              | ExecutionStatus.SUCCEEDED
    [[instanceId: "1", health: []]]                                | ["D"]                           | ExecutionStatus.RUNNING
    [[instanceId: "1", health: [h("LB", "Up"), h("D", "Up")]]]     | null                            | ExecutionStatus.SUCCEEDED
    [[instanceId: "1", health: [h("LB", "Up"), h("D", "Up")]]]     | []                              | ExecutionStatus.SUCCEEDED
    [[instanceId: "1", health: [h("LB", "Down"), h("D", "Up")]]]   | ["D"]                           | ExecutionStatus.SUCCEEDED
  }

  private static Map h(String type, String state) {
    return [
      type : type,
      state: state
    ]
  }
}
