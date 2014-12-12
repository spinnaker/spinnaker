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
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.StepExecution
import org.springframework.beans.factory.annotation.Autowired

@CompileStatic
class StageStatusPropagationListener extends StageExecutionListener {

  @Autowired
  StageStatusPropagationListener(ExecutionRepository executionRepository) {
    super(executionRepository)
  }

  @Override
  void beforeTask(Stage stage, StepExecution stepExecution) {
    stage.startTime = stepExecution.startTime.time
    stage.status = ExecutionStatus.RUNNING
    saveStage stage
  }

  @Override
  void afterTask(Stage stage, StepExecution stepExecution) {
    def orcaTaskStatus = stepExecution.executionContext.get("orcaTaskStatus") as ExecutionStatus
    if (orcaTaskStatus) {
      if (orcaTaskStatus.complete) {
        stage.endTime = System.currentTimeMillis()
      }
      stage.status = orcaTaskStatus
    } else {
      stage.endTime = System.currentTimeMillis()
      stage.status = ExecutionStatus.TERMINAL
    }
    saveStage stage
  }

  private void saveStage(Stage stage) {
    if (stage.execution instanceof Pipeline) {
      executionRepository.store(stage.execution as Pipeline)
    } else {
      executionRepository.store(stage.execution as Orchestration)
    }
  }

}
