/*
 * Copyright (c) 2019 Schibsted Media Group.
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
package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification
import spock.lang.Subject

class DeployCloudFormationStageTest extends Specification {

  def builder = new TaskNode.Builder()

  @Subject
  def cloudFormationStage = new DeployCloudFormationStage()

  def "should return CloudFormation execution ID"() {
    given:
    def stage = new StageExecutionImpl(new PipelineExecutionImpl(ExecutionType.PIPELINE, "testApp"), "cf", [:])

    when:
    if (isChangeSet) {
      stage.context.put("isChangeSet", true)
    }
    if (executeChangeSet){
      stage.context.put("executeChangeSet", true)
    }
    cloudFormationStage.taskGraph(stage, builder)

    then:
    builder.graph.size == graphSize

    where:
    isChangeSet | executeChangeSet || graphSize
    false       | false            || 4
    true        | false            || 6
    true        | true             || 11
  }

}
