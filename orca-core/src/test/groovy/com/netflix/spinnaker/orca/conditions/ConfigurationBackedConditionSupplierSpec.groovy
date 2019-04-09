/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.conditions

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.pipeline.WaitForConditionStage
import com.netflix.spinnaker.orca.time.MutableClock
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class ConfigurationBackedConditionSupplierSpec extends Specification {
  def configService = Stub(DynamicConfigService) {
    getConfig(_ as Class, _ as String, _ as Object) >> { type, name, defaultValue -> return defaultValue }
    isEnabled(_ as String, _ as Boolean) >> { flag, defaultValue -> return defaultValue }
  }

  def conditionsConfigurationProperties = new ConditionConfigurationProperties(configService)
  def clock = new MutableClock()

  @Subject
  def conditionSupplier = new ConfigurationBackedConditionSupplier(conditionsConfigurationProperties)

  @Unroll
  def "should return configured conditions"() {
    given:
    def stage = stage {
      refId = "1"
      type = WaitForConditionStage.STAGE_TYPE
      startTime = clock.millis()
      context = ctx
    }

    and:
    conditionsConfigurationProperties.setClusters(clusters)
    conditionsConfigurationProperties.setActiveConditions(activeConditions)

    when:
    def result = conditionSupplier.getConditions(stage)

    then:
    result.size() == numberOfResultingConditions

    where:
    ctx                 | clusters        | activeConditions     | numberOfResultingConditions
    [cluster: "foo"]    | []              | []                   | 0
    [cluster: "foo"]    | ["foo", "bar"]  | []                   | 0
    [cluster: "foo"]    | ["bar"]         | [ "c1", "c2"]        | 0
    [cluster: "foo"]    | ["foo", "bar"]  | [ "c1", "c2"]        | 2
    [cluster: "foo"]    | []              | [ "c1", "c2"]        | 0
  }
}
