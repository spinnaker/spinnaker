/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.model

import spock.lang.Specification
import static com.netflix.spinnaker.orca.pipeline.model.Stage.topologicalSort
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.*
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage
import static java.util.stream.Collectors.toList

class StageSpec extends Specification {

  def "topologicalSort sorts stages with direct relationships"() {
    given:
    def pipeline = pipeline {
      stage {
        refId = "1"
      }
      stage {
        refId = "2"
        requisiteStageRefIds = ["1"]
      }
      stage {
        refId = "3"
        requisiteStageRefIds = ["2"]
      }
    }

    expect:
    with(topologicalSort(pipeline.stages).collect(toList())) {
      refId == ["1", "2", "3"]
    }
  }

  def "topologicalSort sorts stages with fork join topology"() {
    given:
    def pipeline = pipeline {
      stage {
        refId = "1"
      }
      stage {
        refId = "2"
        requisiteStageRefIds = ["1"]
      }
      stage {
        refId = "3"
        requisiteStageRefIds = ["1"]
      }
      stage {
        refId = "4"
        requisiteStageRefIds = ["2", "3"]
      }
    }

    expect:
    with(topologicalSort(pipeline.stages).collect(toList())) {
      refId.first() == "1"
      refId.last() == "4"
    }
  }

  def "topologicalSort sorts stages with isolated branches"() {
    given:
    def pipeline = pipeline {
      stage {
        refId = "1"
      }
      stage {
        refId = "2"
        requisiteStageRefIds = ["1"]
      }
      stage {
        refId = "3"
      }
      stage {
        refId = "4"
        requisiteStageRefIds = ["3"]
      }
    }

    expect:
    with(topologicalSort(pipeline.stages).collect(toList())) {
      "1" in refId[0..1]
      "3" in refId[0..1]
      "2" in refId[2..3]
      "4" in refId[2..3]
    }
  }

  def "topologicalSort only considers top-level stages"() {
    given:
    def pipeline = pipeline {
      stage {
        refId = "1"
        stage {
          refId = "1<1"
        }
        stage {
          refId = "1<2"
          requisiteStageRefIds = ["1<1"]
        }
        stage {
          refId = "1>1"
          syntheticStageOwner = STAGE_AFTER
        }
      }
      stage {
        refId = "2"
        requisiteStageRefIds = ["1"]
      }
    }

    expect:
    with(topologicalSort(pipeline.stages).collect(toList())) {
      refId == ["1", "2"]
    }
  }

  def "topologicalSort does not go into an infinite loop if given a bad set of stages"() {
    given:
    def pipeline = pipeline {
      stage {
        refId = "1"
        requisiteStageRefIds = ["2"]
      }
      stage {
        refId = "2"
        requisiteStageRefIds = ["1"]
      }
    }

    when:
    topologicalSort(pipeline.stages)

    then:
    def e = thrown(IllegalStateException)
    println e.message
  }

  def "ancestors of a STAGE_AFTER stage should include STAGE_BEFORE siblings"() {
    given:
    def pipeline = pipeline {
      stage {
        refId = "1"
        type = "test"
        stage {
          refId = "1<1"
          syntheticStageOwner = STAGE_BEFORE
        }
        stage {
          refId = "1<2"
          syntheticStageOwner = STAGE_AFTER
        }
      }
    }

    def syntheticBeforeStage = pipeline.stages.find { it.syntheticStageOwner == STAGE_BEFORE }
    def syntheticAfterStage = pipeline.stages.find { it.syntheticStageOwner == STAGE_AFTER }

    when:
    def syntheticBeforeStageAncestors = syntheticBeforeStage.ancestors()
    def syntheticAfterStageAncestors = syntheticAfterStage.ancestors()

    then:
    syntheticBeforeStageAncestors*.refId == ["1<1", "1"]
    syntheticAfterStageAncestors*.refId == ["1<2", "1<1", "1"]
  }
}
