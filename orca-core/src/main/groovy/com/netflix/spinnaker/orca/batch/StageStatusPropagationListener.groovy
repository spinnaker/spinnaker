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
import com.netflix.spinnaker.orca.pipeline.PipelineStage
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.listener.StepExecutionListenerSupport

@Singleton
@CompileStatic
class StageStatusPropagationListener extends StepExecutionListenerSupport {

  @Override
  void beforeStep(StepExecution stepExecution) {
    currentStage(stepExecution).status = PipelineStatus.RUNNING
  }

  @Override
  ExitStatus afterStep(StepExecution stepExecution) {
    def stage = currentStage(stepExecution)
    stage.status = PipelineStatus.valueOf(stepExecution.exitStatus.exitDescription)
    super.afterStep(stepExecution)
  }

  private PipelineStage currentStage(StepExecution stepExecution) {
    def stageName = stepExecution.stepName.find(/^\w+(?=\.)/)
    (PipelineStage) stepExecution.jobExecution.executionContext.get(stageName)
  }
}
