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

package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class StageBuilderSpec extends Specification {
  @Shared
  def execution = new Pipeline()

  @Shared
  def uuidRegex = '.' * 36 /* super hack */

  @Unroll
  def "should use parent stage id and counter to build child stage id"() {
    when:
    def stage1 = StageBuilder.newStage(execution, null, "NewStage!@#", [:], parent as Stage, null)

    then:
    stage1.id =~ expectedStage1Id

    when:
    def stage2 = StageBuilder.newStage(execution, null, "NewStage!@#", [:], parent as Stage, null)

    then:
    stage2.id =~ expectedStage2Id

    where:
    parent                                              || expectedStage1Id           || expectedStage2Id
    buildParent(execution, "ParentId")                  || "ParentId-1-NewStage"      || "ParentId-2-NewStage"
    buildParent(execution, "GrandParentId", "ParentId") || "GrandParentId-1-NewStage" || "GrandParentId-2-NewStage"
    null                                                || uuidRegex                  || uuidRegex
  }

  Stage buildParent(Execution execution, String... parentStageIds) {
    def stages = [] as List<Stage>

    parentStageIds.each {
      def parent = new PipelineStage()
      parent.id = it

      if (stages) {
        parent.parentStageId = stages[-1].id
      }

      stages << parent
    }

    execution.stages.addAll(stages)
    return stages[-1]
  }
}
