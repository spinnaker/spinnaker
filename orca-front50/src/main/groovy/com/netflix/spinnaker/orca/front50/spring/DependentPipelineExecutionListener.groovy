/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.front50.spring

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.front50.DependentPipelineStarter
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.transform.CompileDynamic
import groovy.util.logging.Slf4j
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobExecutionListener
import org.springframework.core.annotation.Order

@Slf4j
@Order(0)
@CompileDynamic
class DependentPipelineExecutionListener implements JobExecutionListener {

  protected final ExecutionRepository executionRepository
  private final Front50Service front50Service
  private DependentPipelineStarter dependentPipelineStarter

  DependentPipelineExecutionListener(ExecutionRepository executionRepository, Front50Service front50Service, DependentPipelineStarter dependentPipelineStarter) {
    this.executionRepository = executionRepository
    this.front50Service = front50Service
    this.dependentPipelineStarter = dependentPipelineStarter
  }

  @Override
  void beforeJob(JobExecution jobExecution) {
  }

  @Override
  void afterJob(JobExecution jobExecution) {
    def execution = currentExecution(jobExecution)
    if (execution && execution.pipelineConfigId) {
      front50Service.getAllPipelines().each {
        it.triggers.each { trigger ->
          if (trigger.enabled &&
            trigger.type == 'pipeline' &&
            trigger.pipeline &&
            trigger.pipeline == execution.pipelineConfigId &&
            trigger.status.contains(convertStatus(execution))
          ) {
            dependentPipelineStarter.trigger(it, execution.trigger?.user, execution, [:], null)
          }
        }
      }
    }
  }

  protected final Execution currentExecution(JobExecution jobExecution) {
    if (jobExecution.jobParameters.parameters.containsKey("pipeline")) {
      String id = jobExecution.jobParameters.getString("pipeline")
      executionRepository.retrievePipeline(id)
    } else {
      null
    }
  }

  private static String convertStatus(Execution execution) {
    switch (execution.status) {
      case ExecutionStatus.CANCELED:
        return 'canceled'
        break
      case ExecutionStatus.SUSPENDED:
        return 'suspended'
        break
      case ExecutionStatus.SUCCEEDED:
        return 'successful'
        break
      default:
        return 'failed'
    }
  }

}
