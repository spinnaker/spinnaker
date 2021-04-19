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
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.ModelUtils
import com.netflix.spinnaker.orca.clouddriver.model.HealthState
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.clouddriver.model.HealthState.*

class WaitForUpInstanceHealthTaskSpec extends Specification {
  @Shared
  PipelineExecutionImpl pipeline = PipelineExecutionImpl.newPipeline("orca")

  CloudDriverService cloudDriverService = Mock()
  def task = new WaitForUpInstanceHealthTask(cloudDriverService)

  @Unroll("#interestingHealthProviderNames instance: #instance => status: #result.status")
  def "test if instance with a given interested health provider name should be considered up"() {
    given:
    def inputs = new InstanceHealthCheckInputs(
        interestingHealthProviderNames: interestingHealthProviderNames,
        instanceIds: ['id'],
        region: 'reg',
        account: 'acct')

    if (interestingHealthProviderNames != []) {
      1 * cloudDriverService.getInstanceTyped(inputs.getAccount(), inputs.getRegion(), 'id') >> ModelUtils.instance(instance)
    }

    expect:
    task.process(inputs).status == shouldBeUp ? ExecutionStatus.RUNNING : ExecutionStatus.SUCCEEDED

    where:
    interestingHealthProviderNames || instance                                                               || shouldBeUp
    []                             || [health: []]                                                           || false
    []                             || [health: null]                                                         || false
    null                           || [health: [Amazon(Unknown)]]                                            || false
    []                             || [health: [Amazon(Unknown)]]                                            || false
    ["Amazon"]                     || [health: [Amazon(Unknown)]]                                            || true
    ["Amazon", "Discovery"]        || [health: [Amazon(Unknown), Discovery(Down)]]                           || false
    ["Amazon", "Discovery"]        || [health: [Amazon(Unknown), Discovery(OutOfService)]]                   || false
    null                           || [health: [Amazon(Unknown), Discovery(OutOfService), LoadBalancer(Up)]] || false
    null                           || [health: [Amazon(Unknown), Discovery(Up), LoadBalancer(Down)]]         || false
    null                           || [health: [Amazon(Unknown), Discovery(Up), LoadBalancer(Up)]]           || true
    null                           || [health: [Amazon(Unknown), Discovery(Up), LoadBalancer(Unknown)]]      || true
    ["Discovery"]                  || [health: [Discovery(Down)]]                                            || false
    ["Discovery"]                  || [health: [Discovery(OutOfService)]]                                    || false
    ["Discovery", "Other"]         || [health: [Other(Down)]]                                                || false
    ["Amazon"]                     || [health: []]                                                           || false
    ["Amazon"]                     || [health: [Amazon(Up)]]                                                 || true
    ["Amazon", "Discovery"]        || [health: [Amazon(Unknown), Discovery(Up)]]                             || true
    ["Discovery"]                  || [health: [Discovery(Up)]]                                              || true
    ["Discovery"]                  || [health: [Other(Up)]]                                                  || false
    ["Discovery", "Other"]         || [health: [Other(Up)]]                                                  || true
    ["Discovery"]                  || [health: [Other(Down)]]                                                || false
  }

  def Amazon(HealthState state) {
    return [type: "Amazon", healthClass: "platform", state: state.name()]
  }

  def Discovery(HealthState state) {
    return [type: "Discovery", state: state.name()]
  }

  def Other(HealthState state) {
    return [type: "Other", state: state.name()]
  }

  def LoadBalancer(HealthState state) {
    return [type: "LoadBalancer", state: state.name()]
  }

  @Unroll
  void 'should be #expectedResult for #instanceDetails with #interestingHealthProviderNames relevant health providers (WaitForUpInstances)'() {
    given:
    def localInstanceDetails = instanceDetails.collect { ModelUtils.instance(it) }
    def stage = new StageExecutionImpl(pipeline, "waitForDownInstance", [
        instanceIds: localInstanceDetails*.instanceId,
        interestingHealthProviderNames: interestingHealthProviderNames
    ])

    cloudDriverService.getInstanceTyped(_, _, _) >> { a, r, instanceId -> localInstanceDetails.find { it.instanceId == instanceId } }

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
        type: type,
        state: state
    ]
  }
}
