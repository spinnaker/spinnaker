/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class EnsureInterestingHealthProviderNamesTaskSpec extends Specification {
  def interestingHealthProviderNamesSuppliers = Mock(TitusInterestingHealthProviderNamesSupplier)

  @Subject
  def task = new EnsureInterestingHealthProviderNamesTask([interestingHealthProviderNamesSuppliers])

  @Unroll
  def "should ensure interesting health provider names"() {
    given:
    def stage = new PipelineStage(new Pipeline(), "", [:])

    and:
    interestingHealthProviderNamesSuppliers.supports(_,_ as Stage) >> supports
    interestingHealthProviderNamesSuppliers.process(_,_ as Stage) >> interestingHealthProviderNames

    when:
    def taskResult = task.execute(stage)

    then:
    taskResult.status == ExecutionStatus.SUCCEEDED
    (taskResult.getStageOutputs() as Map) == expectedStageOutputs

    where:
    supports            | interestingHealthProviderNames || expectedStageOutputs
    false               | ['Titus']                      || [:]
    true                | ['Titus']                      || [interestingHealthProviderNames: ["Titus"]]
    true                | null                           || [:]
    true                | []                             || [interestingHealthProviderNames: []]
  }
}
