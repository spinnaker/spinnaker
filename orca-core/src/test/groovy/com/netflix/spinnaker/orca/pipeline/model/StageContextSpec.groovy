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
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class StageContextSpec extends Specification {

  def pipeline = pipeline {
    context.foo = "global-foo"
    context.bar = "global-bar"
    context.baz = "global-baz"
    context.qux = "global-qux"
    context.fnord = "global-fnord"
    stage {
      refId = "1"
      outputs.foo = "root-foo"
      outputs.bar = "root-bar"
      outputs.baz = "root-baz"
      outputs.qux = "root-qux"
    }
    stage {
      refId = "2"
      requisiteStageRefIds = ["1"]
      outputs.foo = "ancestor-foo"
      outputs.bar = "ancestor-bar"
      outputs.baz = "ancestor-baz"
    }
    stage {
      refId = "3"
      requisiteStageRefIds = ["2"]
      outputs.foo = "parent-foo"
      outputs.bar = "parent-bar"
    }
    stage {
      refId = "3>1"
      parentStageId = execution.stageByRef("3").id
      syntheticStageOwner = STAGE_AFTER
      context.foo = "current-foo"
    }
    stage {
      refId = "4"
      outputs.covfefe = "unrelated-covfefe"
    }
    stage {
      refId = "3>2"
      requisiteStageRefIds = ["3>1"]
      parentStageId = execution.stageByRef("3").id
      syntheticStageOwner = STAGE_AFTER
      outputs.covfefe = "downstream-covfefe"
    }
  }

  def "a stage's own context takes priority"() {
    expect:
    pipeline.stageByRef("3>1").context.foo == "current-foo"
  }

  def "parent takes priority over ancestor"() {
    expect:
    pipeline.stageByRef("3>1").context.bar == "parent-bar"
  }

  def "missing keys resolve in ancestor stage context"() {
    expect:
    pipeline.stageByRef("3>1").context.baz == "ancestor-baz"
  }

  def "can chain back up ancestors"() {
    expect:
    pipeline.stageByRef("3>1").context.qux == "root-qux"
  }

  def "can't resolve keys from unrelated or downstream stages"() {
    expect:
    pipeline.stageByRef("3>1").context.covfefe == null
  }

  def "if all else fails will read from global context"() {
    expect:
    pipeline.stageByRef("3>1").context.fnord == "global-fnord"
  }
}
