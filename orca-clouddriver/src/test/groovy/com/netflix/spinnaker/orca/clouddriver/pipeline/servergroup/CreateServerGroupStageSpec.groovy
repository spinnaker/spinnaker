/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Task

import java.util.concurrent.TimeUnit
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.RollbackClusterStage
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class CreateServerGroupStageSpec extends Specification {
  @Subject
  def createServerGroupStage = new CreateServerGroupStage(
    rollbackClusterStage: new RollbackClusterStage(null, null)
  )

  @Unroll
  def "should build RollbackStage when 'rollbackOnFailure' is enabled"() {
    given:
    def stage = stage {
      context = [
        "deploy.server.groups": deployServerGroups,
        "application"         : "myapplication",
        "account"             : "test",
        "cloudProvider"       : "aws",
        "strategy"            : strategy
      ]
      tasks = [
        new Task(status: failedTask ? ExecutionStatus.TERMINAL : ExecutionStatus.SUCCEEDED)
      ]
    }

    if (shouldRollbackOnFailure) {
      stage.context.rollback = [
        onFailure: true
      ]
    }

    when:
    def graph = StageGraphBuilder.afterStages(stage)
    createServerGroupStage.onFailureStages(stage, graph)
    def onFailureStageContexts = graph.build()*.getContext()

    then:
    onFailureStageContexts == expectedOnFailureStageContexts

    where:
    shouldRollbackOnFailure | strategy          | deployServerGroups                          | failedTask || expectedOnFailureStageContexts
    false                   | "rollingredblack" | null                                        | false      || []
    true                    | "rollingredblack" | null                                        | false      || []
    false                   | "rollingredblack" | ["us-west-1": ["myapplication-stack-v001"]] | false      || []
    true                    | "redblack"        | ["us-west-1": ["myapplication-stack-v001"]] | false      || []      // only rollback if task has failed
    true                    | "highlander"      | ["us-west-1": ["myapplication-stack-v001"]] | false      || []      // highlander is not supported
    true                    | "rollingredblack" | ["us-west-1": ["myapplication-stack-v001"]] | false      || [expectedContext([enableAndDisableOnly: true])]
    true                    | "redblack"        | ["us-west-1": ["myapplication-stack-v001"]] | true       || [expectedContext([disableOnly: true])]
  }


  Map expectedContext(Map<String, Object> additionalRollbackContext) {
    return [
      regions                  : ["us-west-1"],
      serverGroup              : "myapplication-stack-v001",
      credentials              : "test",
      cloudProvider            : "aws",
      stageTimeoutMs           : TimeUnit.MINUTES.toMillis(30),
      additionalRollbackContext: additionalRollbackContext
    ]
  }
}
