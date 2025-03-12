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

package com.netflix.spinnaker.orca.clouddriver.tasks.instance

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.ModelUtils
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Shared

import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import spock.lang.Specification
import spock.lang.Unroll

class WaitForDownInstanceHealthTaskSpec extends Specification {
  @Shared
  PipelineExecutionImpl pipeline = PipelineExecutionImpl.newPipeline("orca")

  CloudDriverService cloudDriverService = Mock()
  def task = new WaitForDownInstanceHealthTask(cloudDriverService)

  @Unroll("#interestedHealthProviderNames with instance: #instance => status: #shouldBeDown")
  def "test if instance with a given interested health provider name should be considered down"() {
    given:
    def inputs = new InstanceHealthCheckInputs(
        interestingHealthProviderNames: interestedHealthProviderNames,
        instanceIds: ['id'],
        region: 'reg',
        account: 'acct')

    if (interestedHealthProviderNames != []) {
      1 * cloudDriverService.getInstanceTyped(inputs.getAccount(), inputs.getRegion(), 'id') >> ModelUtils.instance(instance)
    }
    expect:
    task.process(inputs).status == shouldBeDown ? ExecutionStatus.RUNNING : ExecutionStatus.SUCCEEDED

    where:
    interestedHealthProviderNames || instance                                                                                                            || shouldBeDown
    []                            || [health: []]                                                                                                        || true
    []                            || [health: null]                                                                                                      || true
    ["Amazon"]                    || [health: [[type: "Amazon", healthClass: "platform", state: "Unknown"]]]                                             || true
    ["Amazon", "Discovery"]       || [health: [[type: "Amazon", healthClass: "platform", state: "Unknown"], [type: "Discovery", state: "Down"]]]         || true
    ["Amazon", "Discovery"]       || [health: [[type: "Amazon", healthClass: "platform", state: "Unknown"], [type: "Discovery", state: "OutOfService"]]] || true
    ["Discovery"]                 || [health: [[type: "Discovery", state: "Down"]]]                                                                      || true
    ["Discovery"]                 || [health: [[type: "Discovery", state: "OutOfService"]]]                                                              || true
    ["Discovery", "Other"]        || [health: [[type: "Other", state: "Down"]]]                                                                          || true
    ["LoadBalancer"]              || [health: []]                                                                                                        || true
    ["Amazon"]                    || [health: [[type: "Amazon", healthClass: "platform", state: "Up"]]]                                                  || false
    ["Amazon", "Discovery"]       || [health: [[type: "Amazon", healthClass: "platform", state: "Unknown"], [type: "Discovery", state: "Up"]]]           || false
    ["Discovery"]                 || [health: [[type: "Discovery", state: "Up"]]]                                                                        || false
    ["Discovery"]                 || [health: [[type: "Other", state: "Up"]]]                                                                            || true
    ["Discovery", "Other"]        || [health: [[type: "Other", state: "Up"]]]                                                                            || false
    ["Discovery"]                 || [health: [[type: "Other", state: "Down"]]]                                                                          || true
  }

  @Unroll
  void 'should be #expectedResult for #instanceDetails with #interestingHealthProviderNames relevant health providers (WaitForDownInstances)'() {
    given:
    def localInstanceDetails = instanceDetails.collect { ModelUtils.instance(it) }
    cloudDriverService.getInstanceTyped(_, _, _) >> { a, r, instanceId -> localInstanceDetails.find { it.instanceId == instanceId } }

    def stage = new StageExecutionImpl(pipeline, "waitForDownInstance", [
        instanceIds: localInstanceDetails*.instanceId,
        interestingHealthProviderNames: interestingHealthProviderNames
    ])

    expect:
    task.execute(stage).status == expectedResult

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

  private static Map h(String type, String state) {
    return [
        type: type,
        state: state
    ]
  }
}
