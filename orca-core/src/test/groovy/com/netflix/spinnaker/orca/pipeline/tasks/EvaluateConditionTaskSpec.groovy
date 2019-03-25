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

package com.netflix.spinnaker.orca.pipeline.tasks

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.conditions.Condition
import com.netflix.spinnaker.orca.conditions.ConditionSupplier
import com.netflix.spinnaker.orca.conditions.ConditionConfigurationProperties
import com.netflix.spinnaker.orca.time.MutableClock
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import java.time.Duration

import static com.netflix.spinnaker.orca.pipeline.WaitForConditionStage.*
import static com.netflix.spinnaker.orca.pipeline.WaitForConditionStage.WaitForConditionContext.Status.*
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class EvaluateConditionTaskSpec extends Specification {
  def conditionSupplier = Mock(ConditionSupplier)
  def configService = Stub(DynamicConfigService) {
    getConfig(_ as Class, _ as String, _ as Object) >> { type, name, defaultValue -> return defaultValue }
    isEnabled(_ as String, _ as Boolean) >> { flag, defaultValue -> return defaultValue }
  }

  def conditionsConfigurationProperties = new ConditionConfigurationProperties(configService)
  def clock = new MutableClock()

  @Subject
  def task = new EvaluateConditionTask(
    conditionsConfigurationProperties,
    [conditionSupplier],
    clock
  )

  def "should wait for conditions"() {
    given:
    def stage = stage {
      type = STAGE_TYPE
      startTime = clock.millis()
      context = [
        status: WAITING
      ]
    }

    and:
    conditionsConfigurationProperties.setBackoffWaitMs(5)

    when:
    def result = task.execute(stage)

    then:
    0 * conditionSupplier.getConditions(stage)
    result.status == ExecutionStatus.RUNNING

    when:
    conditionsConfigurationProperties.setBackoffWaitMs(5)
    clock.incrementBy(Duration.ofMillis(5))

    and:
    result = task.execute(stage)

    then:
    1 * conditionSupplier.getConditions(stage) >> [new Condition("a", "b")]
    result.status == ExecutionStatus.RUNNING

    when:
    result = task.execute(stage)

    then:
    1 * conditionSupplier.getConditions(stage) >> []
    result.status == ExecutionStatus.SUCCEEDED

    when:
    stage.context.status = SKIPPED
    result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    0 * conditionSupplier.getConditions(stage)
  }

  @Unroll
  def "should wait for conditions when enabled"() {
    given:
    def stage = stage {
      refId = "1"
      type = STAGE_TYPE
      startTime = clock.millis()
      context = ctx
    }

    and:
    conditionsConfigurationProperties.setEnabled(true)
    conditionsConfigurationProperties.setBackoffWaitMs(4)
    clock.incrementBy(Duration.ofMillis(5))

    when:
    def result = task.execute(stage)

    then:
    conditionSupplier.getConditions(stage) >> conditions
    result.status == executionStatus
    result.getContext().status == waitStatus

    where:
    ctx                 | conditions                      | waitStatus | executionStatus
    [:]                 | []                              | SKIPPED    | ExecutionStatus.SUCCEEDED
    [status: WAITING]   | []                              | SKIPPED    | ExecutionStatus.SUCCEEDED
    [status: SKIPPED]   | []                              | SKIPPED    | ExecutionStatus.SUCCEEDED
    [status: WAITING]   | [new Condition("n", "d")]       | WAITING    | ExecutionStatus.RUNNING
  }
}
