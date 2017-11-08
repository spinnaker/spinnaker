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

import java.util.List;
import java.util.Objects;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.listeners.ExecutionListener;
import com.netflix.spinnaker.orca.listeners.Persister;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE;

/**
 * Reacts to pipelines finishing and schedules the next job waiting
 */
public class PipelineStarterListener implements ExecutionListener {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final ExecutionRepository executionRepository;
  private final PipelineStartTracker startTracker;
  private final ApplicationContext applicationContext;

  @Autowired
  public PipelineStarterListener(ExecutionRepository executionRepository,
                                 PipelineStartTracker startTracker,
                                 ApplicationContext applicationContext) {
    this.executionRepository = executionRepository;
    this.startTracker = startTracker;
    this.applicationContext = applicationContext;
  }

  @Override
  public void afterExecution(Persister persister,
                             Execution execution,
                             ExecutionStatus executionStatus,
                             boolean wasSuccessful) {
    startTracker.getAllStartedExecutions().forEach(startedExecutionId -> {
      try {
        Execution e = executionRepository.retrieve(PIPELINE, startedExecutionId);
        if (e.getStatus().isComplete()) {
          processPipelines(e);
        }
      } catch (ExecutionNotFoundException ignored) {
        log.warn("Unable to update pipeline status for missing execution (executionId: {})", startedExecutionId);
      } catch (Exception e) {
        log.error("failed to update pipeline status", e);
      }
    });
  }

  private void processPipelines(Execution execution) {
    log.info("marking pipeline finished {}", execution.getId());
    startTracker.markAsFinished(execution.getPipelineConfigId(), execution.getId());
    if (execution.getPipelineConfigId() != null) {
      List<String> queuedPipelines = startTracker.getQueuedPipelines(execution.getPipelineConfigId());
      if (!queuedPipelines.isEmpty()) {
        // pipelines are stored in a stack...
        // if we are keeping waiting pipelines, take the oldest one; otherwise, take the most recent
        int nextIndex = execution.isKeepWaitingPipelines() ? queuedPipelines.size() - 1 : 0;
        String nextPipelineId = queuedPipelines.get(nextIndex);
        queuedPipelines.forEach(id -> {
          if (Objects.equals(id, nextPipelineId)) {
            Execution queuedExecution = executionRepository.retrieve(PIPELINE, id);
            log.info("starting pipeline {} due to {} ending", nextPipelineId, execution.getId());
            try {
              getExecutionLauncher().start(queuedExecution);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
            startTracker.removeFromQueue(execution.getPipelineConfigId(), id);
          } else if (!execution.isKeepWaitingPipelines()) {
            log.info("marking pipeline {} as canceled due to {} ending", nextPipelineId, execution.getId());
            executionRepository.cancel(id);
            startTracker.removeFromQueue(execution.getPipelineConfigId(), id);
          } // else we want to keep the pipeline in the queue
        });
      }
    }
  }

  // break circular dependency

  protected ExecutionLauncher getExecutionLauncher() {
    return applicationContext.getBean(ExecutionLauncher.class);
  }
}
