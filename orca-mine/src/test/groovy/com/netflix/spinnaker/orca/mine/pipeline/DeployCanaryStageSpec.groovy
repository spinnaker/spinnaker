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

package com.netflix.spinnaker.orca.mine.pipeline

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.mine.pipeline.DeployCanaryStage.CompleteDeployCanaryTask
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification

class DeployCanaryStageSpec extends Specification {

  def "should short-circuit and return in a TERMINAL status if any deploy stages are not successful"() {
    setup:
    Pipeline pipeline = new Pipeline()
    String parentStageId = "a"

    Stage successfulDeploymentStage = new Stage<Pipeline>(pipeline, "deploy", [:])
    successfulDeploymentStage.parentStageId = parentStageId
    successfulDeploymentStage.status = ExecutionStatus.SUCCEEDED

    Stage failedDeploymentStage = new Stage<Pipeline>(pipeline, "deploy", [:])
    failedDeploymentStage.parentStageId = parentStageId
    failedDeploymentStage.status = ExecutionStatus.FAILED_CONTINUE

    Stage stage = new Stage<Pipeline>(pipeline, "the stage", [:])
    stage.id = parentStageId

    pipeline.stages = [stage, failedDeploymentStage, successfulDeploymentStage]
    CompleteDeployCanaryTask task = new CompleteDeployCanaryTask(Optional.empty(), null)

    expect:
    task.execute(stage).status == ExecutionStatus.TERMINAL
  }
}
