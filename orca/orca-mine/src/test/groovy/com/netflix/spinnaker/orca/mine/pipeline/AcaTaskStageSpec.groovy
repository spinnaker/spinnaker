package com.netflix.spinnaker.orca.mine.pipeline

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.TaskExecutionImpl
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import retrofit2.mock.Calls
import spock.lang.Specification

/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

class AcaTaskStageSpec extends Specification {

  def "restart aca task should cancel off the original canary and clean up the stage context"() {
    given:
    def executionRepository = Mock(ExecutionRepository)
    def pipeline = PipelineExecutionImpl.newPipeline("orca")

    def canary = createCanary('123');
    def context = [canary: canary.clone()]
    StageExecutionImpl stage = createStage(pipeline, context)
    AcaTaskStage acaTaskStage = new AcaTaskStage()
    MineService mineService = Mock()
    acaTaskStage.mineService = mineService

    when:
    acaTaskStage.prepareStageForRestart(stage)

    then: "canary should be copied to the restart details"
    stage.context.restartDetails.previousCanary == canary

    and: "preserve the canary config"
    stage.context.canary.canaryConfig == canary.canaryConfig

    and: "the deployment details"
    stage.context.canary.canaryDeployments == canary.canaryDeployments

    and: "clean up the canary"
    stage.context.canary.id == null
    stage.context.canary.launchDate == null
    stage.context.canary.endDate == null
    stage.context.canary.canaryResult == null
    stage.context.canary.status == null
    stage.context.canary.health == null
    stage.status == ExecutionStatus.NOT_STARTED

    and: "the canary should be cancelled"
    1 * mineService.cancelCanary(_, _) >> Calls.response(null)
  }

  def "restart aca task should not cancel off the original canary if there is no canary id and clean up the stage context"() {
    given:
    def executionRepository = Mock(ExecutionRepository)
    def pipeline = PipelineExecutionImpl.newPipeline("orca")

    def canary = createCanary()
    def context = [canary: canary.clone()]
    StageExecutionImpl stage = createStage(pipeline, context)
    AcaTaskStage acaTaskStage = new AcaTaskStage()
    MineService mineService = Mock()
    acaTaskStage.mineService = mineService

    when:
    acaTaskStage.prepareStageForRestart(stage)

    then: "canary should be copied to the restart details"
    stage.context.restartDetails.previousCanary == canary

    and: "preserve the canary config"
    stage.context.canary.canaryConfig == canary.canaryConfig

    and: "the deployment details"
    stage.context.canary.canaryDeployments == canary.canaryDeployments

    and: "clean up the canary"
    stage.context.canary.id == null
    stage.context.canary.launchDate == null
    stage.context.canary.endDate == null
    stage.context.canary.canaryResult == null
    stage.context.canary.status == null
    stage.context.canary.health == null
    stage.status == ExecutionStatus.NOT_STARTED

    and: "the canceled call of the canary should not be called"
    0 * mineService.cancelCanary(_, _)
  }

  def createCanary(String id) {
    def canary = [
      launchDate       : 1470062664495,
      endDate          : 1470070824033,
      canaryConfig     : [id: 1, application: "cadmium"],
      canaryDeployments: [[id: 2], [id: 3]],
      canaryResult     : [overallResult: 20, overallScore: 89],
      status           : [status: "COMPLETED"],
      health           : [health: "UNKNOWN"]
    ]

    if (id) {
      canary.id = id
    }
    canary
  }

  def createStage(PipelineExecutionImpl pipeline, Map<String, Object> context) {
    StageExecutionImpl stage = new StageExecutionImpl(pipeline, "acaTask", "ACA Task", context)
    stage.tasks = [
      new TaskExecutionImpl(
        id: "1",
        name: "stageStart",
        startTime: 1470062659330,
        endTime: 1470062660513,
        status: "SUCCEEDED"
      ),
      new TaskExecutionImpl(
        id: "2",
        name: "registerGenericCanary",
        startTime: 1470062663868,
        endTime: 1470062664805,
        status: "SUCCEEDED"
      ),
      new TaskExecutionImpl(
        id: "3",
        name: "monitorGenericCanary",
        startTime: 1470062668621,
        endTime: 1470070825533,
        status: "CANCELED"
      ),
    ]
    stage
  }
}


