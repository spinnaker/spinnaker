/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.kayenta.events;

import com.netflix.kayenta.canary.CanaryExecutionStatusResponse;
import com.netflix.kayenta.canary.ExecutionMapper;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class CanaryExecutionCompletedProducer extends AbstractExecutionCompleteEventProcessor {

  private final ExecutionMapper executionMapper;

  @Autowired
  public CanaryExecutionCompletedProducer(ApplicationEventPublisher applicationEventPublisher,
                                          ExecutionRepository executionRepository,
                                          ExecutionMapper executionMapper) {

    super(applicationEventPublisher, executionRepository);
    this.executionMapper = executionMapper;
  }

  @Override
  public boolean shouldProcessExecution(Execution execution) {
    return ExecutionMapper.PIPELINE_NAME.equals(execution.getName());
  }

  @Override
  public void processCompletedPipelineExecution(Execution execution) {
    CanaryExecutionStatusResponse canaryExecutionStatusResponse = executionMapper.fromExecution(execution);
    CanaryExecutionCompletedEvent canaryExecutionCompletedEvent = new CanaryExecutionCompletedEvent(this, canaryExecutionStatusResponse);
    applicationEventPublisher.publishEvent(canaryExecutionCompletedEvent);
  }
}
