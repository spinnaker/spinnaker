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
import com.netflix.spinnaker.orca.clouddriver.model.HealthState
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.clouddriver.model.HealthState.*

class WaitForUpInstanceHealthTaskSpec extends Specification {

  CloudDriverService cloudDriverService = Mock()
  def task = new WaitForUpInstanceHealthTask(cloudDriverService: cloudDriverService)

  @Unroll("#interestingHealthProviderNames instance: #instance => shouldBeUp: #shouldBeUp")
  def "test if instance with a given interested health provider name should be considered up"() {
    given:
    def inputs = new InstanceHealthCheckInputs(
        interestingHealthProviderNames: interestingHealthProviderNames,
        instanceIds: ['id'],
        region: 'reg',
        account: 'acct')

    if (interestingHealthProviderNames != []) {
      1 * cloudDriverService.getInstance(inputs.getAccount(), inputs.getRegion(), 'id') >> instance
    }

    expect:
    task.hasSucceeded(instance, interestingHealthProviderNames) == shouldBeUp
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
}
