/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.mine.pipeline

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import org.springframework.batch.core.Step
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class MonitorCanaryStageSpec extends Specification {

  @Shared
  def stageBuilder = Spy(MonitorCanaryStage)

  def setupSpec() {
    stageBuilder.buildStep(_, _, _) >> { Stub(Step) }
  }

  @Unroll
  void "should set stage timeout to #hours hours based on canary context of #context"() {

    given:
    def pipelineStage = new PipelineStage(new Pipeline(), "monitor", context)

    when:
    stageBuilder.buildSteps(pipelineStage)

    then:
    pipelineStage.context.stageTimeoutMs == hours * 60 * 60 * 1000

    where:
    context                                          || hours
    [:]                                              || 48
    [canary: [:]]                                    || 48
    [canary: [canaryConfig: [:]]]                    || 48
    [canary: [canaryConfig: [lifetimeHours: "n/a"]]] || 48
    [canary: [canaryConfig: [lifetimeHours: "0"]]]   || 2
    [canary: [canaryConfig: [lifetimeHours: "1"]]]   || 3
    [canary: [canaryConfig: [lifetimeHours: "100"]]] || 102
    [canary: [canaryConfig: [lifetimeHours: 0]]]     || 2
    [canary: [canaryConfig: [lifetimeHours: 1]]]     || 3
    [canary: [canaryConfig: [lifetimeHours: 8]]]     || 10
  }
}
