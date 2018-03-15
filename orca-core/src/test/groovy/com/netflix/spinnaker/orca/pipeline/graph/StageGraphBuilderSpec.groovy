/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.graph

import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder.afterStages
import static com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder.beforeStages
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class StageGraphBuilderSpec extends Specification {

  @Shared def parent = stage {
    refId = "1"
  }

  @Unroll
  def "adding a single #expectedSyntheticType stage configures it correctly"() {
    given:
    subject.add {
      it.name = "A"
    }
    def stages = subject.build().toList()

    expect:
    stages.size() == 1

    and:
    with(stages.first()) {
      execution == parent.execution
      parentStageId == parent.id
      syntheticStageOwner == expectedSyntheticType
      name == "A"
      refId != null
    }

    where:
    subject              || expectedSyntheticType
    beforeStages(parent) || STAGE_BEFORE
    afterStages(parent)  || STAGE_AFTER
  }

  @Unroll
  def "adding two dependent #expectedSyntheticType stages wires up refId relationships"() {
    given:
    def stage1 = subject.add {
      it.name = "A"
    }
    def stage2 = subject.connect(stage1) {
      it.name = "B"
    }
    def stages = subject.build().toList()

    expect:
    stages.size() == 2

    and:
    stage2.requisiteStageRefIds.size() == 1
    stage2.requisiteStageRefIds.first() == stage1.refId

    where:
    subject              || expectedSyntheticType
    beforeStages(parent) || STAGE_BEFORE
    afterStages(parent)  || STAGE_AFTER
  }

  @Unroll
  def "connecting multiple #expectedSyntheticType stages wires up refId relationships"() {
    given:
    def stage1 = subject.add {
      it.name = "A"
    }
    def stage2 = subject.add {
      it.name = "B1"
    }
    def stage3 = subject.add {
      it.name = "B2"
    }
    def stage4 = subject.add {
      it.name = "C"
    }
    subject.connect(stage1, stage2)
    subject.connect(stage1, stage3)
    subject.connect(stage2, stage4)
    subject.connect(stage3, stage4)
    def stages = subject.build().toList()

    expect:
    stages.size() == 4

    and:
    stage2.requisiteStageRefIds == [stage1.refId] as Set
    stage3.requisiteStageRefIds == [stage1.refId] as Set
    stage4.requisiteStageRefIds == [stage2.refId, stage3.refId] as Set

    where:
    subject              || expectedSyntheticType
    beforeStages(parent) || STAGE_BEFORE
    afterStages(parent)  || STAGE_AFTER

    stageType = "covfefe"
  }

  def "prefixing a stage wires up refId relationships"() {
    given:
    def stage0 = new Stage(name: "Z")
    def subject = beforeStages(parent, stage0)

    def stage1 = subject.add {
      it.name = "A1"
    }
    def stage2 = subject.add {
      it.name = "A2"
    }
    def stage3 = subject.connect(stage1) {
      it.name = "B"
    }
    subject.connect(stage2, stage3)
    subject.build()

    expect:
    stage1.requisiteStageRefIds == [stage0.refId] as Set
    stage2.requisiteStageRefIds == [stage0.refId] as Set

    and:
    stage3.requisiteStageRefIds == [stage1.refId, stage2.refId] as Set
  }

}
