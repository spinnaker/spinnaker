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

import com.netflix.spinnaker.orca.pipeline.persistence.PipelineQueue
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class PipelineStarterQueue {

  @Autowired
  PipelineQueue pipelineQueue

  static final String PIPELINE_STARTED = "PIPELINE:STARTED"
  static final String PIPELINE_QUEUED = "PIPELINE:QUEDED"
  static final String PIPELINE_STARTED_ALL = "PIPELINE:STARTED_ALL"

  boolean hasStartedExecutions(String pipelineConfigId) {
    pipelineQueue.contains("${PIPELINE_STARTED}:${pipelineConfigId}")
  }

  void addToStarted(String pipelineConfigId, String executionId) {
    pipelineQueue.add("${PIPELINE_STARTED}:${pipelineConfigId}", executionId)
    pipelineQueue.add(PIPELINE_STARTED_ALL, executionId)
  }

  void addToQueue(String pipelineConfigId, String executionId) {
    pipelineQueue.add("${PIPELINE_QUEUED}:${pipelineConfigId}", executionId)
  }

  void markAsFinished(String pipelineConfigId, String executionId) {
    pipelineQueue.remove("${PIPELINE_STARTED}:${pipelineConfigId}", executionId)
    pipelineQueue.remove(PIPELINE_STARTED_ALL, executionId)
  }

  List<String> getAllStartedExecutions() {
    pipelineQueue.elements(PIPELINE_STARTED_ALL)
  }

  List<String> getQueuedPipelines(String pipelineConfigId) {
    pipelineQueue.elements("${PIPELINE_QUEUED}:${pipelineConfigId}")
  }

  void removeFromQueue(String pipelineConfigId, String executionId) {
    pipelineQueue.remove("${PIPELINE_QUEUED}:${pipelineConfigId}", executionId)
  }

}
