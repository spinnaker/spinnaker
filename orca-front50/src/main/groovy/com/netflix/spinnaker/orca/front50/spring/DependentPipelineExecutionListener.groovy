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

import groovy.transform.CompileDynamic
import groovy.util.logging.Slf4j
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.front50.DependentPipelineStarter
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.listeners.ExecutionListener
import com.netflix.spinnaker.orca.listeners.Persister
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline

@Slf4j
@CompileDynamic
class DependentPipelineExecutionListener implements ExecutionListener {

  private final Front50Service front50Service
  private DependentPipelineStarter dependentPipelineStarter

  DependentPipelineExecutionListener(Front50Service front50Service, DependentPipelineStarter dependentPipelineStarter) {
    this.front50Service = front50Service
    this.dependentPipelineStarter = dependentPipelineStarter
  }

  @Override
  void afterExecution(Persister persister, Execution execution, ExecutionStatus executionStatus, boolean wasSuccessful) {
    if (!execution || !(execution instanceof Pipeline)) {
      return
    }

    def pipelineExecution = (Pipeline) execution
    def status = convertStatus(pipelineExecution)

    front50Service.getAllPipelines().findAll { !it.disabled }.each {
      it.triggers.each { trigger ->
        if (trigger.enabled &&
          trigger.type == 'pipeline' &&
          trigger.pipeline &&
          trigger.pipeline == pipelineExecution.pipelineConfigId &&
          trigger.status.contains(status)
        ) {
          dependentPipelineStarter.trigger(it, pipelineExecution.trigger?.user as String, pipelineExecution, [:], null)
        }
      }
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

  @Override
  int getOrder() {
    return HIGHEST_PRECEDENCE
  }
}
