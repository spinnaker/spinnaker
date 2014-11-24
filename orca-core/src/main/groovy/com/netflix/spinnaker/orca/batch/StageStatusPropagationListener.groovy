/*
 * Copyright 2014 Netflix, Inc.
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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.*
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.transform.CompileStatic
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.listener.StepExecutionListenerSupport
import org.springframework.beans.factory.annotation.Autowired

@CompileStatic
class StageStatusPropagationListener extends StepExecutionListenerSupport {

  private final ExecutionRepository executionRepository

  @Autowired
  StageStatusPropagationListener(ExecutionRepository executionRepository) {
    this.executionRepository = executionRepository
  }

  @Override
  void beforeStep(StepExecution stepExecution) {
    currentStage(stepExecution).status = ExecutionStatus.RUNNING
  }

  @Override
  ExitStatus afterStep(StepExecution stepExecution) {
    def stage = currentStage(stepExecution)

    def orcaTaskStatus = stepExecution.executionContext.get("orcaTaskStatus") as ExecutionStatus
    if (orcaTaskStatus) {
      stage.status = orcaTaskStatus
    } else {
      stage.status = ExecutionStatus.TERMINAL
    }

    if (stage.execution instanceof Pipeline) {
      executionRepository.store(stage.execution as Pipeline)
    } else {
      executionRepository.store(stage.execution as Orchestration)
    }

    super.afterStep(stepExecution)
  }

  private Execution currentPipeline(StepExecution stepExecution) {
    if (stepExecution.jobParameters.parameters.containsKey("pipeline")) {
      String id = stepExecution.jobParameters.getString("pipeline")
      executionRepository.retrievePipeline(id)
    } else {
      String id = stepExecution.jobParameters.getString("orchestration")
      executionRepository.retrieveOrchestration(id)
    }
  }

  private Stage currentStage(StepExecution stepExecution) {
    currentPipeline(stepExecution).namedStage(stageName(stepExecution))
  }

  private static String stageName(StepExecution stepExecution) {
    stepExecution.stepName.tokenize(".").first()
  }
}
