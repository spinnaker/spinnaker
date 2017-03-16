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
import com.netflix.spinnaker.orca.pipeline.model.Pipeline;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * Reacts to pipelines finishing and schedules the next job waiting
 */
@Slf4j
public class PipelineStarterListener implements ExecutionListener {

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
        Pipeline e = executionRepository.retrievePipeline(startedExecutionId);
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

  private void processPipelines(Pipeline execution) {
    log.info("marking pipeline finished {}", execution.getId());
    startTracker.markAsFinished(execution.getPipelineConfigId(), execution.getId());
    if (execution.getPipelineConfigId() != null) {
      List<String> queuedPipelines = startTracker.getQueuedPipelines(execution.getPipelineConfigId());
      if (!queuedPipelines.isEmpty()) {
        String nextPipelineId = queuedPipelines.get(0);
        queuedPipelines.forEach(id -> {
          if (Objects.equals(id, nextPipelineId)) {
            Pipeline queuedExecution = executionRepository.retrievePipeline(id);
            log.info("starting pipeline {} due to {} ending", nextPipelineId, execution.getId());
            try {
              getPipelineLauncher().start(queuedExecution);
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

  protected ExecutionLauncher<Pipeline> getPipelineLauncher() {
    return applicationContext.getBean(PipelineLauncher.class);
  }
}
