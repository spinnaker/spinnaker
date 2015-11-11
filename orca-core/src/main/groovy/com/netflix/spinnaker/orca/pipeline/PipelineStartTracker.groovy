/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.PipelineStack
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import rx.schedulers.Schedulers

@Slf4j
@Component
@CompileStatic
class PipelineStartTracker {

  @Autowired
  PipelineStack pipelineStack

  @Autowired
  ExecutionRepository executionRepository

  static final String PIPELINE_STARTED = "PIPELINE:STARTED"
  static final String PIPELINE_QUEUED = "PIPELINE:QUEUED"
  static final String PIPELINE_STARTED_ALL = "PIPELINE:STARTED_ALL"
  static final String PIPELINE_QUEUED_ALL = "PIPELINE:QUEUED_ALL"

  void addToStarted(String pipelineConfigId, String executionId) {
    if (pipelineConfigId) {
      pipelineStack.add("${PIPELINE_STARTED}:${pipelineConfigId}", executionId)
    }
    pipelineStack.add(PIPELINE_STARTED_ALL, executionId)
  }

  boolean queueIfNotStarted(String pipelineConfigId, String executionId) {
    def allRunningExecutionIds = executionRepository.retrievePipelinesForPipelineConfigId(
      pipelineConfigId,
      new ExecutionRepository.ExecutionCriteria(limit: Integer.MAX_VALUE, statuses: [ExecutionStatus.RUNNING.toString()])
    ).subscribeOn(Schedulers.io()).toList().toBlocking().single()*.id

    getStartedPipelines(pipelineConfigId).each { String eId ->
      if (!allRunningExecutionIds.contains(eId)) {
        log.info("No running execution found for `${pipelineConfigId}:${eId}`, marking as finished")
        markAsFinished(pipelineConfigId, eId)
      }
    }

    boolean isQueued = pipelineStack.addToListIfKeyExists("${PIPELINE_STARTED}:${pipelineConfigId}", "${PIPELINE_QUEUED}:${pipelineConfigId}", executionId)
    if (isQueued) {
      pipelineStack.add(PIPELINE_QUEUED_ALL, executionId)
    }
    isQueued
  }

  void markAsFinished(String pipelineConfigId, String executionId) {
    if (pipelineConfigId) {
      pipelineStack.remove("${PIPELINE_STARTED}:${pipelineConfigId}", executionId)
    }
    pipelineStack.remove(PIPELINE_STARTED_ALL, executionId)
  }

  List<String> getAllStartedExecutions() {
    pipelineStack.elements(PIPELINE_STARTED_ALL)
  }

  List<String> getAllWaitingExecutions() {
    pipelineStack.elements(PIPELINE_QUEUED_ALL)
  }

  List<String> getQueuedPipelines(String pipelineConfigId) {
    pipelineStack.elements("${PIPELINE_QUEUED}:${pipelineConfigId}")
  }

  List<String> getStartedPipelines(String pipelineConfigId) {
    pipelineStack.elements("${PIPELINE_STARTED}:${pipelineConfigId}")
  }

  void removeFromQueue(String pipelineConfigId, String executionId) {
    pipelineStack.remove("${PIPELINE_QUEUED}:${pipelineConfigId}", executionId)
    pipelineStack.remove(PIPELINE_QUEUED_ALL, executionId)
  }

}
