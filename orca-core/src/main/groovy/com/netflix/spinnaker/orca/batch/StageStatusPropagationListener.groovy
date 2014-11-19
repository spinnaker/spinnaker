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

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.PipelineStatus
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.PipelineStore
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.listener.StepExecutionListenerSupport
import org.springframework.beans.factory.annotation.Autowired

@CompileStatic
class StageStatusPropagationListener extends StepExecutionListenerSupport {

  private final PipelineStore pipelineStore

  @Autowired
  StageStatusPropagationListener(PipelineStore pipelineStore) {
    this.pipelineStore = pipelineStore
  }

  @Override
  void beforeStep(StepExecution stepExecution) {
    currentStage(stepExecution).status = PipelineStatus.RUNNING
  }

  @Override
  ExitStatus afterStep(StepExecution stepExecution) {
    def stage = currentStage(stepExecution)

    def orcaTaskStatus = stepExecution.executionContext.get("orcaTaskStatus") as PipelineStatus
    if (orcaTaskStatus) {
      stage.status = orcaTaskStatus
    } else {
      stage.status = PipelineStatus.TERMINAL
    }

    pipelineStore.store(stage.pipeline)

    super.afterStep(stepExecution)
  }

  private Pipeline currentPipeline(StepExecution stepExecution) {
    String id = stepExecution.jobParameters.getString("pipeline")
    pipelineStore.retrieve(id)
  }

  private Stage currentStage(StepExecution stepExecution) {
    currentPipeline(stepExecution).namedStage(stageName(stepExecution))
  }

  private static String stageName(StepExecution stepExecution) {
    stepExecution.stepName.tokenize(".").first()
  }
}
