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

package com.netflix.spinnaker.orca.pipeline;

import java.util.Collections;
import java.util.List;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria;
import com.netflix.spinnaker.orca.pipeline.persistence.PipelineStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rx.schedulers.Schedulers;
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

@Component
public class PipelineStartTracker {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final PipelineStack pipelineStack;
  private final ExecutionRepository executionRepository;

  private static final String PIPELINE_STARTED = "PIPELINE:STARTED";
  private static final String PIPELINE_QUEUED = "PIPELINE:QUEUED";
  private static final String PIPELINE_STARTED_ALL = "PIPELINE:STARTED_ALL";
  private static final String PIPELINE_QUEUED_ALL = "PIPELINE:QUEUED_ALL";

  @Autowired
  public PipelineStartTracker(PipelineStack pipelineStack, ExecutionRepository executionRepository) {
    this.pipelineStack = pipelineStack;
    this.executionRepository = executionRepository;
  }

  public void addToStarted(String pipelineConfigId, String executionId) {
    if (pipelineConfigId != null) {
      pipelineStack.add(format("%s:%s", PIPELINE_STARTED, pipelineConfigId), executionId);
    }
    pipelineStack.add(PIPELINE_STARTED_ALL, executionId);
  }

  public boolean queueIfNotStarted(String pipelineConfigId, String executionId) {
    ExecutionCriteria criteria = new ExecutionCriteria();
    criteria.setLimit(Integer.MAX_VALUE);
    criteria.setStatuses(Collections.singleton(RUNNING.toString()));
    List<String> allRunningExecutionIds = executionRepository
      .retrievePipelinesForPipelineConfigId(pipelineConfigId, criteria)
      .subscribeOn(Schedulers.io())
      .toList()
      .toBlocking()
      .single()
      .stream()
      .map(Execution::getId)
      .collect(toList());

    getStartedPipelines(pipelineConfigId).forEach(eId -> {
      if (!allRunningExecutionIds.contains(eId)) {
        log.info("No running execution found for `{}:{}`, marking as finished", pipelineConfigId, eId);
        markAsFinished(pipelineConfigId, eId);
      }
    });

    boolean isQueued = pipelineStack.addToListIfKeyExists("${PIPELINE_STARTED}:${pipelineConfigId}", "${PIPELINE_QUEUED}:${pipelineConfigId}", executionId);
    if (isQueued) {
      pipelineStack.add(PIPELINE_QUEUED_ALL, executionId);
    }
    return isQueued;
  }

  public void markAsFinished(String pipelineConfigId, String executionId) {
    if (pipelineConfigId != null) {
      pipelineStack.remove(format("%s:%s", PIPELINE_STARTED, pipelineConfigId), executionId);
    }
    pipelineStack.remove(PIPELINE_STARTED_ALL, executionId);
  }

  public List<String> getAllStartedExecutions() {
    return pipelineStack.elements(PIPELINE_STARTED_ALL);
  }

  public List<String> getAllWaitingExecutions() {
    return pipelineStack.elements(PIPELINE_QUEUED_ALL);
  }

  public List<String> getQueuedPipelines(String pipelineConfigId) {
    return pipelineStack.elements(format("%s:%s", PIPELINE_QUEUED, pipelineConfigId));
  }

  public List<String> getStartedPipelines(String pipelineConfigId) {
    return pipelineStack.elements(format("%s:%s", PIPELINE_STARTED, pipelineConfigId));
  }

  public void removeFromQueue(String pipelineConfigId, String executionId) {
    pipelineStack.remove(format("%s:%s", PIPELINE_QUEUED, pipelineConfigId), executionId);
    pipelineStack.remove(PIPELINE_QUEUED_ALL, executionId);
  }

}
