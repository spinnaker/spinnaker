/*
 * Copyright (c) 2019 Nike, inc.
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

import com.netflix.spinnaker.orca.events.ExecutionComplete;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;

public abstract class AbstractExecutionCompleteEventProcessor implements ApplicationListener<ExecutionComplete> {
  protected final ApplicationEventPublisher applicationEventPublisher;
  private final ExecutionRepository executionRepository;

  @Autowired
  public AbstractExecutionCompleteEventProcessor(ApplicationEventPublisher applicationEventPublisher,
                                                 ExecutionRepository executionRepository) {

    this.applicationEventPublisher = applicationEventPublisher;
    this.executionRepository = executionRepository;
  }

  @Override
  public void onApplicationEvent(ExecutionComplete event) {
    if (event.getExecutionType() != Execution.ExecutionType.PIPELINE) {
      return;
    }
    Execution execution = executionRepository.retrieve(Execution.ExecutionType.PIPELINE, event.getExecutionId());
    if (shouldProcessExecution(execution)) {
      processCompletedPipelineExecution(execution);
    }
  }

  public abstract boolean shouldProcessExecution(Execution execution);

  public abstract void processCompletedPipelineExecution(Execution execution);
}
