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
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.batch.core.StepExecution
import org.springframework.beans.factory.annotation.Autowired

@CompileStatic
@Slf4j
class StageStatusPropagationListener extends AbstractStagePropagationListener {

  @Autowired
  StageStatusPropagationListener(ExecutionRepository executionRepository) {
    super(executionRepository)
  }

  @Override
  void beforeTask(Stage stage, StepExecution stepExecution) {
    if (!stage.startTime) {
      stage.startTime = System.currentTimeMillis()
    }
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
}
